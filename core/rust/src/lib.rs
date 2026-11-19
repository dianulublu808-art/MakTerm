//! Native process supervisor for MakTerm.
//!
//! Kotlin builds the platform-specific command line (Android shell or the
//! proot/chroot `init-host` wrapper) and passes it here as JSON. Rust owns the
//! child process, merges its output into a bounded log and optionally restarts
//! it after an unexpected exit.
//!
//! The UI polls `nativeSnapshot` and `nativeLog`; there are no JNI callbacks,
//! which keeps the boundary small and avoids attaching Rust threads to the JVM.

use std::collections::HashMap;
use std::io::{BufReader, Read};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicI64, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};

const MAX_LOG: usize = 120_000;
const RESTART_DELAY: Duration = Duration::from_secs(3);

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Config {
    id: String,
    program: Vec<String>,
    #[serde(default)]
    env: HashMap<String, String>,
    #[serde(default)]
    work_dir: String,
    #[serde(default)]
    keep_alive: bool,
    #[serde(default = "default_max_restarts")]
    max_restarts: u32,
}

fn default_max_restarts() -> u32 {
    5
}

struct ProcState {
    id: String,
    config: Mutex<Config>,
    running: AtomicBool,
    stopping: AtomicBool,
    restarting: AtomicBool,
    started_at: AtomicI64,
    exit_code: AtomicI32,
    restarts: AtomicU32,
    pid: AtomicI32,
    /// Incremented on every start/stop so a stale supervisor thread can detect
    /// that it no longer owns the process and must not touch shared state.
    generation: AtomicU64,
    log: Mutex<String>,
}

impl ProcState {
    fn new(id: String) -> Self {
        Self {
            id,
            config: Mutex::new(Config {
                id: String::new(),
                program: Vec::new(),
                env: HashMap::new(),
                work_dir: String::new(),
                keep_alive: false,
                max_restarts: default_max_restarts(),
            }),
            running: AtomicBool::new(false),
            stopping: AtomicBool::new(false),
            restarting: AtomicBool::new(false),
            started_at: AtomicI64::new(0),
            exit_code: AtomicI32::new(-999),
            restarts: AtomicU32::new(0),
            pid: AtomicI32::new(0),
            generation: AtomicU64::new(1),
            log: Mutex::new(String::new()),
        }
    }

    fn reset(&self, config: Config) -> u64 {
        let generation = self.generation.fetch_add(1, Ordering::SeqCst) + 1;
        let mut slot = self.config.lock().unwrap();
        *slot = config;
        self.stopping.store(false, Ordering::SeqCst);
        self.restarting.store(false, Ordering::SeqCst);
        self.running.store(false, Ordering::SeqCst);
        self.exit_code.store(-999, Ordering::SeqCst);
        self.restarts.store(0, Ordering::SeqCst);
        self.pid.store(0, Ordering::SeqCst);
        self.started_at.store(0, Ordering::SeqCst);
        self.log.lock().unwrap().clear();
        generation
    }

    fn is_current(&self, generation: u64) -> bool {
        self.generation.load(Ordering::SeqCst) == generation
    }

    fn append(&self, text: &str) {
        if text.is_empty() {
            return;
        }
        let mut log = self.log.lock().unwrap();
        log.push_str(text);
        if log.len() > MAX_LOG {
            let keep_from = log.len() - MAX_LOG;
            // Keep the tail. String is UTF-8, so find the next char boundary.
            let mut cut = keep_from;
            while cut < log.len() && !log.is_char_boundary(cut) {
                cut += 1;
            }
            log.drain(..cut);
        }
    }
}

fn registry() -> &'static Mutex<HashMap<String, Arc<ProcState>>> {
    static REGISTRY: OnceLock<Mutex<HashMap<String, Arc<ProcState>>>> = OnceLock::new();
    REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn state_for(id: &str) -> Arc<ProcState> {
    let mut map = registry().lock().unwrap();
    map.entry(id.to_string())
        .or_insert_with(|| Arc::new(ProcState::new(id.to_string())))
        .clone()
}

fn spawn_child(state: &Arc<ProcState>) -> Result<Child, String> {
    let config = state.config.lock().unwrap().clone();
    if config.program.is_empty() {
        return Err("Empty program".to_string());
    }

    let mut command = Command::new(&config.program[0]);
    command.args(&config.program[1..]);
    command.envs(&config.env);
    if !config.work_dir.is_empty() {
        command.current_dir(&config.work_dir);
    }
    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    let mut child = command
        .spawn()
        .map_err(|error| format!("{}: {error}", config.program[0]))?;

    state.pid.store(child.id() as i32, Ordering::SeqCst);
    state.started_at.store(now_millis(), Ordering::SeqCst);
    state.running.store(true, Ordering::SeqCst);
    state.exit_code.store(-999, Ordering::SeqCst);

    if let Some(stdout) = child.stdout.take() {
        spawn_reader(state.clone(), stdout);
    }
    if let Some(stderr) = child.stderr.take() {
        spawn_reader(state.clone(), stderr);
    }

    Ok(child)
}

fn spawn_reader<R: Read + Send + 'static>(state: Arc<ProcState>, stream: R) {
    thread::spawn(move || {
        let mut reader = BufReader::new(stream);
        let mut buffer = [0u8; 4096];
        loop {
            match reader.read(&mut buffer) {
                Ok(0) => break,
                Ok(read) => {
                    let text = String::from_utf8_lossy(&buffer[..read]);
                    state.append(&text);
                }
                Err(_) => break,
            }
        }
    });
}

fn supervisor(state: Arc<ProcState>, mut child: Child, generation: u64) {
    loop {
        let status = child.wait();
        if !state.is_current(generation) {
            // A newer start/stop owns this process now; leave its state alone.
            return;
        }
        state.pid.store(0, Ordering::SeqCst);
        state.running.store(false, Ordering::SeqCst);

        let code = match &status {
            Ok(status) => status.code().unwrap_or(-1),
            Err(_) => -1,
        };
        state.exit_code.store(code, Ordering::SeqCst);
        state.append(&format!("\n■ Процесс завершён, код {code}\n"));

        let (keep_alive, max_restarts) = {
            let config = state.config.lock().unwrap();
            (config.keep_alive, config.max_restarts)
        };
        let restarts = state.restarts.load(Ordering::SeqCst);
        let should_restart = !state.stopping.load(Ordering::SeqCst)
            && keep_alive
            && restarts < max_restarts;

        if !should_restart {
            state.restarting.store(false, Ordering::SeqCst);
            break;
        }

        let next = restarts + 1;
        state.restarts.store(next, Ordering::SeqCst);
        state.restarting.store(true, Ordering::SeqCst);
        state.append(&format!(
            "↻ Перезапуск через 3 секунды ({next}/{max_restarts})\n"
        ));

        thread::sleep(RESTART_DELAY);

        if !state.is_current(generation) || state.stopping.load(Ordering::SeqCst) {
            if state.is_current(generation) {
                state.restarting.store(false, Ordering::SeqCst);
            }
            break;
        }

        match spawn_child(&state) {
            Ok(next_child) => {
                state.restarting.store(false, Ordering::SeqCst);
                child = next_child;
            }
            Err(error) => {
                state.append(&format!("✖ {error}\n"));
                state.restarting.store(false, Ordering::SeqCst);
                break;
            }
        }
    }
}

fn start(config: Config) -> Result<(), String> {
    if config.id.is_empty() {
        return Err("Empty process id".to_string());
    }
    if config.program.is_empty() {
        return Err("Empty program".to_string());
    }

    let state = state_for(&config.id);
    if state.running.load(Ordering::SeqCst) {
        return Ok(());
    }

    let generation = state.reset(config);
    let child = spawn_child(&state)?;
    thread::spawn(move || supervisor(state, child, generation));
    Ok(())
}

fn stop(id: &str) {
    let state = match registry().lock().unwrap().get(id).cloned() {
        Some(state) => state,
        None => return,
    };

    // Invalidate the supervisor before changing anything, so a process that
    // exits right now cannot overwrite the state of a newer start.
    state.generation.fetch_add(1, Ordering::SeqCst);
    state.stopping.store(true, Ordering::SeqCst);
    state.running.store(false, Ordering::SeqCst);
    state.restarting.store(false, Ordering::SeqCst);

    let pid = state.pid.swap(0, Ordering::SeqCst);
    if pid > 0 {
        // Ask nicely first, then make sure the process is gone. The supervisor
        // thread owns `Child`, so the signal is sent by pid.
        unsafe {
            libc::kill(pid, libc::SIGTERM);
        }
        thread::spawn(move || {
            thread::sleep(Duration::from_millis(1500));
            unsafe {
                libc::kill(pid, libc::SIGKILL);
            }
        });
    }
}

fn stop_all() {
    let ids: Vec<String> = registry().lock().unwrap().keys().cloned().collect();
    for id in ids {
        stop(&id);
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SnapshotEntry {
    id: String,
    running: bool,
    restarting: bool,
    started_at: i64,
    exit_code: i32,
    restarts: u32,
}

fn snapshot_json() -> String {
    let entries: Vec<SnapshotEntry> = registry()
        .lock()
        .unwrap()
        .values()
        .map(|state| SnapshotEntry {
            id: state.id.clone(),
            running: state.running.load(Ordering::SeqCst),
            restarting: state.restarting.load(Ordering::SeqCst),
            started_at: state.started_at.load(Ordering::SeqCst),
            exit_code: state.exit_code.load(Ordering::SeqCst),
            restarts: state.restarts.load(Ordering::SeqCst),
        })
        .collect();
    serde_json::to_string(&entries).unwrap_or_else(|_| "[]".to_string())
}

fn log_text(id: &str) -> String {
    registry()
        .lock()
        .unwrap()
        .get(id)
        .map(|state| state.log.lock().unwrap().clone())
        .unwrap_or_default()
}

fn clear_log(id: &str) {
    if let Some(state) = registry().lock().unwrap().get(id) {
        state.log.lock().unwrap().clear();
    }
}

fn new_jstring(env: &mut JNIEnv, text: &str) -> jstring {
    env.new_string(text)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn jstring_to_string(env: &mut JNIEnv, value: &JString) -> Result<String, String> {
    env.get_string(value)
        .map(|text| text.into())
        .map_err(|error| format!("JNI string: {error}"))
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeStart(
    mut env: JNIEnv,
    _this: JObject,
    config_json: JString,
) -> jstring {
    let input = match jstring_to_string(&mut env, &config_json) {
        Ok(input) => input,
        Err(error) => return new_jstring(&mut env, &error),
    };
    let config: Config = match serde_json::from_str(&input) {
        Ok(config) => config,
        Err(error) => return new_jstring(&mut env, &format!("Config: {error}")),
    };
    match start(config) {
        Ok(()) => new_jstring(&mut env, ""),
        Err(error) => new_jstring(&mut env, &error),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeStop(
    mut env: JNIEnv,
    _this: JObject,
    id: JString,
) {
    if let Ok(id) = jstring_to_string(&mut env, &id) {
        stop(&id);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeStopAll(
    _env: JNIEnv,
    _this: JObject,
) {
    stop_all();
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeClearLog(
    mut env: JNIEnv,
    _this: JObject,
    id: JString,
) {
    if let Ok(id) = jstring_to_string(&mut env, &id) {
        clear_log(&id);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeLog(
    mut env: JNIEnv,
    _this: JObject,
    id: JString,
) -> jstring {
    let text = match jstring_to_string(&mut env, &id) {
        Ok(id) => log_text(&id),
        Err(_) => String::new(),
    };
    new_jstring(&mut env, &text)
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeSnapshot(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    let text = snapshot_json();
    new_jstring(&mut env, &text)
}

#[no_mangle]
pub extern "system" fn Java_com_rk_server_RustProcessHost_nativeVersion(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    new_jstring(&mut env, env!("CARGO_PKG_VERSION"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_id(prefix: &str) -> String {
        format!("{prefix}-{}-{}", std::process::id(), now_millis())
    }

    fn wait_for<F: Fn() -> bool>(predicate: F, timeout: Duration) -> bool {
        let started = std::time::Instant::now();
        while started.elapsed() < timeout {
            if predicate() {
                return true;
            }
            thread::sleep(Duration::from_millis(50));
        }
        predicate()
    }

    #[test]
    fn runs_and_captures_output() {
        let id = test_id("log");
        start(Config {
            id: id.clone(),
            program: vec![
                "/system/bin/sh".to_string(),
                "-c".to_string(),
                "echo hello; sleep 0.1; echo world".to_string(),
            ],
            env: HashMap::new(),
            work_dir: String::new(),
            keep_alive: false,
            max_restarts: 0,
        })
        .unwrap();

        assert!(wait_for(
            || log_text(&id).contains("world"),
            Duration::from_secs(5)
        ));
        assert!(wait_for(
            || !state_for(&id).running.load(Ordering::SeqCst),
            Duration::from_secs(5)
        ));
    }

    #[test]
    fn stop_terminates_a_long_running_process() {
        let id = test_id("stop");
        start(Config {
            id: id.clone(),
            program: vec![
                "/system/bin/sh".to_string(),
                "-c".to_string(),
                "sleep 30".to_string(),
            ],
            env: HashMap::new(),
            work_dir: String::new(),
            keep_alive: false,
            max_restarts: 0,
        })
        .unwrap();

        assert!(wait_for(
            || state_for(&id).running.load(Ordering::SeqCst),
            Duration::from_secs(2)
        ));
        stop(&id);
        assert!(wait_for(
            || !state_for(&id).running.load(Ordering::SeqCst),
            Duration::from_secs(5)
        ));
    }
}
