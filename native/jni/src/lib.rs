use jni::objects::{JClass, JString, JValue};
use jni::sys::jboolean;
use jni::{JNIEnv, JavaVM};
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use tokio::time::{sleep, Duration};
use livecraft_tiktok_core::{TikTokLive, structs::TikTokLiveEvent};

static RUNNING: AtomicBool = AtomicBool::new(false);

fn status(vm: &JavaVM, message: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(class), Ok(value)) = (env.find_class("com/livecraft/GameBridgeService"), env.new_string(message)) {
            let _ = env.call_static_method(class, "onNativeTikTokStatus", "(Ljava/lang/String;)V", &[JValue::Object(&value)]);
        }
    }
}

fn chat(vm: &JavaVM, username: &str, comment: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(user), Ok(message), Ok(class)) = (
            env.new_string(username),
            env.new_string(comment),
            env.find_class("com/livecraft/GameBridgeService"),
        ) {
            let _ = env.call_static_method(class, "onNativeTikTokChat", "(Ljava/lang/String;Ljava/lang/String;)V",
                &[JValue::Object(&user), JValue::Object(&message)]);
        }
    }
}

fn gift(vm: &JavaVM, name: &str, coins: i64, combo: bool, streak_over: bool) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(class), Ok(value)) = (env.find_class("com/livecraft/GameBridgeService"), env.new_string(name)) {
            let _ = env.call_static_method(class, "onNativeTikTokGift", "(Ljava/lang/String;JZZ)V",
                &[JValue::Object(&value), JValue::Long(coins), JValue::Bool(combo as jboolean), JValue::Bool(streak_over as jboolean)]);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livecraft_TikTokNative_nativeStart(mut env: JNIEnv, _class: JClass, username: JString) {
    if RUNNING.swap(true, Ordering::SeqCst) { return; }
    let username: String = match env.get_string(&username) {
        Ok(value) => value.into(),
        Err(_) => { RUNNING.store(false, Ordering::SeqCst); return; }
    };
    let vm = match env.get_java_vm() {
        Ok(value) => value,
        Err(_) => { RUNNING.store(false, Ordering::SeqCst); return; }
    };
    thread::spawn(move || {
        let runtime = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
            Ok(value) => value,
            Err(error) => { status(&vm, &format!("Falha ao iniciar TikTok: {error}")); RUNNING.store(false, Ordering::SeqCst); return; }
        };
        runtime.block_on(async move {
            status(&vm, &format!("Conectando à live de @{username}..."));
            let mut stream = match TikTokLive::builder(&username).connect().await {
                Ok(value) => value,
                Err(error) => { status(&vm, &format!("Erro TikTok: {error}")); RUNNING.store(false, Ordering::SeqCst); return; }
            };
            status(&vm, &format!("Conectado à live de @{username}. Aguardando comentários e presentes."));
            while RUNNING.load(Ordering::SeqCst) {
                tokio::select! {
                    result = stream.next_event() => match result {
                        Ok(TikTokLiveEvent::Chat(event)) => {
                            let author = event.user.as_ref().map(|user| if !user.nickname.is_empty() { user.nickname.as_str() } else { user.unique_id.as_str() }).unwrap_or("TikTok");
                            chat(&vm, author, &event.comment);
                        }\n                        Ok(TikTokLiveEvent::Gift(event)) => {
                            let name = event.gift_details.as_ref().map(|details| details.gift_name.as_str()).unwrap_or("Presente");
                            gift(&vm, name, event.diamond_total(), event.is_combo_gift(), event.is_streak_over());
                        }
                        Ok(TikTokLiveEvent::Disconnected) => status(&vm, "Live desconectada. Reconectando..."),
                        Ok(TikTokLiveEvent::Reconnecting { attempt, max_retries, .. }) => status(&vm, &format!("Reconectando TikTok ({attempt}/{max_retries})...")),
                        Ok(_) => {},
                        Err(error) => { status(&vm, &format!("Conexão TikTok encerrada: {error}")); break; }
                    },
                    _ = sleep(Duration::from_millis(250)) => {}
                }
            }
            drop(stream);
            status(&vm, "Conexão TikTok parada.");
            RUNNING.store(false, Ordering::SeqCst);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_livecraft_TikTokNative_nativeStop(_env: JNIEnv, _class: JClass) {
    RUNNING.store(false, Ordering::SeqCst);
}
