# TriLingual AI Android — v0.1.1 (Hybrid starter)

โครงการแอปแปลเสียงไทย/English/中文（简体） เป็นโค้ด Android จริง ไม่ใช่ภาพตัวอย่างเว็บ
**สถานะ: source delivered; CI build and real-phone end-to-end tests NOT YET VERIFIED.**

## สำคัญสำหรับผู้ที่ติดตั้ง v0.1.0 แล้ว

APK ที่ได้จาก GitHub Actions รุ่นเก่าและรุ่นใหม่ใช้ debug signing key คนละชุด จึง **ไม่สามารถติดตั้งทับ v0.1.0 ได้**. เพื่อไม่ให้การถอนแอปเก่าลบข้อมูลประวัติ ตั้งแต่ v0.1.1 debug APK เปลี่ยนเป็น applicationId `com.trilingual.ai.preview` และชื่อ "TriLingual AI (ทดสอบ)" ทำให้ติดตั้งแยกจาก v0.1.0 ได้. ประวัติเดิมจะยังอยู่ในแอปเก่า แต่ **ไม่ได้ย้ายมาแอปทดสอบอัตโนมัติ**. บันทึกสำคัญจากรุ่นเก่าควรส่งออก Markdown เก็บไว้ก่อน. การอัปเดตแอปทดสอบรุ่นถัดไปยังต้องใช้ signing key เดิม (ควรตั้งค่า release signing แยกแบบปลอดภัยเมื่อพัฒนาเป็นผลิตภัณฑ์จริง).

## เปลี่ยนแปลง v0.1.1

- หน้าประวัติ: เพิ่มปุ่มถังขยะในบัตรบันทึกแต่ละรายการ พร้อมหน้าต่างยืนยันก่อนลบถาวร
- ลบบันทึกพร้อมข้อความต้นฉบับ คำแปล และสรุปที่เก็บใน SQLite ภายใน transaction เดียว
- ไม่อนุญาตให้ลบรายการที่กำลังบันทึกเสียงอยู่ ต้องหยุดการบันทึกก่อน
- เมื่อไม่มีรายการแล้วจะแสดงข้อความ “ยังไม่มีบันทึก”; ลบรายการที่กำลังเปิดอยู่แล้วล้างรายการที่เลือก

## สิ่งที่สร้างแล้ว

- Android Kotlin + Jetpack Compose + Foreground Service (microphone), min Android 10/API 29.
- System `SpeechRecognizer` ฟังเสียงเป็นช่วง พร้อม partial results, restart หลังผลลัพธ์จบ และกำหนดภาษาไทย/อังกฤษ/จีนเอง.
- ตัวเลือก `createOnDeviceSpeechRecognizer()` สำหรับออฟไลน์ **เฉพาะอุปกรณ์ที่รองรับ** (Android 12+) ไม่แอบ fallback ไปออนไลน์.
- Auto language detection/switching ขอจาก Android 14+ เท่านั้น และ **ขึ้นอยู่กับบริการรู้จำเสียง**; Android 10–13 โหมด Auto เริ่มด้วยภาษาไทย ไม่รับประกันการสลับสามภาษา.
- ML Kit แปลข้อความออฟไลน์เมื่อดาวน์โหลดโมเดลสำเร็จ; กดเตรียมโมเดลจากหน้า Settings. ไทย↔จีนอาจใช้ pivot ภาษาอังกฤษภายใน ML Kit.
- ตัวเลือก online AI ข้อความ ผ่าน HTTPS OpenAI-compatible Chat Completions **เฉพาะเมื่อผู้ใช้เปิดและใส่ API key**. ใช้ทั้งการแปลและสรุปประชุม; เมื่อแปล AI ไม่สำเร็จจะลอง ML Kit.
- API key เข้ารหัสโดย Android Keystore + AES-GCM ก่อนจัดเก็บใน SharedPreferences; ไม่ฝังคีย์ไว้ใน repository.
- Android TextToSpeech อ่านข้อความ; ต้องติดตั้ง voice ของภาษาปลายทางบนอุปกรณ์.
- โหมดประชุม, บันทึกช่วงเสียงที่สมบูรณ์ลง SQLite ทันที ก่อนเริ่มแปล, เก็บประวัติและส่งออก Markdown.
- สรุปเบื้องต้นแบบ extractive **ไม่ใช่ AI**; โหมด AI สรุปเมื่อเชื่อมต่อบริการผู้ใช้เอง.
- โหมดนำเสนอตัวใหญ่, Picture-in-Picture, overlay พร้อมขอสิทธิ์ Android.
- คำศัพท์เฉพาะสำหรับ biasing intent (ถ้าระบบรองรับ) และ prompt ออนไลน์.
- กำหนดผู้พูด 1–4 **ด้วยตนเอง**. **ยังไม่มี AI speaker diarization.**
- GitHub Actions build APK debug และ run JVM unit tests; ไม่มีคีย์/บริการเสียเงินในโค้ด.

## สิ่งที่ยังทำไม่ได้ / ต้องพัฒนาเพิ่ม (สำคัญ)

1. **ยังไม่มีระบบแยกผู้พูดอัตโนมัติ**. UI speaker 1–4 เป็นการเลือกเอง ไม่ได้ระบุตัวบุคคลจากเสียง.
2. **ยังไม่มี offline STT engine ที่แอปบันเดิลเอง** เช่น whisper.cpp. การพูดแบบออฟไลน์ใช้ระบบ Android และชุดภาษาที่ติดตั้งในโทรศัพท์เท่านั้น. โทรศัพท์บางรุ่นไม่รองรับภาษาไทย/จีนออฟไลน์.
3. ตัว `SpeechRecognizer` ของ Android ขึ้นกับผู้ผลิต/บริการซอฟต์แวร์ ไม่การันตีการรับฟังไม่สะดุดเป็นชั่วโมง, ถอดเสียงจีนอัตโนมัติ, speaker overlap, หรือตรวจจับภาษา Auto บนอุปกรณ์ทุกรุ่น.
4. โหมด presentation ให้ผู้ใช้ mirror/cast หน้าจอเอง ยังไม่มีระบบส่งคำบรรยายไปยังเว็บผู้เข้าร่วมประชุมแบบ multi-device.
5. ยังไม่รองรับดึงเสียงภายใน Zoom/Meet จากแอปอื่น; Android มีข้อจำกัดการจับเสียงของแอปอื่น.
6. แอปใช้การรู้จำเสียงเป็นช่วง ไม่ใช่ simultaneous word-by-word neural streaming; บริการออนไลน์และการดาวน์โหลดโมเดลอาจทำให้ข้อความแปลล่าช้า.
7. ไม่บันทึกไฟล์เสียงต้นฉบับ แค่ข้อความแต่ละช่วง. แปลที่กำลังประมวลผลขณะที่ Android kill process อาจไม่สมบูรณ์ แม้ข้อความต้นฉบับที่ commit แล้วจะยังอยู่.
8. ยังไม่ได้ build ตรวจสอบบน Android SDK ในสภาพแวดล้อมที่สร้างไฟล์นี้ เพราะไม่ได้ติดตั้ง Android SDK/Gradle และออกอินเทอร์เน็ตไม่ได้. ต้องรัน CI และแก้ compilation errors ที่อาจพบ ก่อนบอกว่า APK ใช้งานได้.
9. ความปลอดภัยของข้อมูลที่ส่งไปยัง AI ออนไลน์ขึ้นกับผู้ให้บริการปลายทาง; หลีกเลี่ยงการส่งข้อมูลลับโดยไม่ได้รับอนุญาต.

## สร้าง APK ด้วย GitHub Actions (ไม่ต้องติดตั้ง Android Studio ในเครื่อง)

1. แตกไฟล์ `TriLingualAI-source.zip` แล้วอัปโหลด **เนื้อหาภายใน** ไปยัง repository GitHub ใหม่ (อย่าทับโปรเจ็กต์เดิม) โดยคงโฟลเดอร์ `.github/workflows/`.
2. กดแท็บ `Actions` → `Build Android APK` → `Run workflow`, หรือ push branch `main`.
3. เมื่อ workflow ผ่าน (เครื่องหมายเขียว) เข้า run → `Artifacts` → ดาวน์โหลด `TriLingualAI-debug-v0.1.1`.
4. แตก ZIP artifact จะพบ `app-debug.apk`, ส่งเข้า Android แล้วติดตั้งจากแหล่งที่เชื่อถือได้. Debug APK สำหรับทดสอบ ยังไม่ใช่ build สำหรับเผยแพร่ Play Store.
5. ถ้าขั้นตอน `:app:assembleDebug` หรือ unit tests ไม่ผ่าน ให้ดู Logs ใน GitHub Actions แล้วแก้ที่ไฟล์ต้นเหตุ. ห้ามถือว่า ZIP source คือ APK.

## ทดลองกับโทรศัพท์จริง

- อนุญาตไมโครโฟน; ทดสอบพูดไทย (เลือก ไทย), อังกฤษ (เลือก EN), จีน (เลือก 中文) ทีละโหมดก่อน.
- เข้า Settings → `ดาวน์โหลดโมเดลแปลภาษาออฟไลน์`, ต้องเชื่อม Wi-Fi ครั้งแรก.
- ถ้าเครื่องรองรับ speech recognition ออฟไลน์ ให้เปิดสวิตช์ และทดสอบ Airplane Mode หลังติดตั้ง language packs ของระบบ Android (ไม่ใช่แค่ ML Kit).
- เมื่อใช้ System Speech แบบปกติ **เสียงอาจส่งไปผู้ให้บริการของระบบ**; ไม่ใช่การรับรองออฟไลน์.
- เปลี่ยนจอแนวตั้ง/แนวนอนระหว่างบันทึก; เสียงอยู่ใน Foreground Service แต่ความต่อเนื่องขึ้นกับ recognition service ของเครื่อง.
- ตั้งค่าคีย์ API ทาง Settings เท่านั้น; ใช้บริการ OpenAI-compatible URL แบบ HTTPS. เปิดสวิตช์ออนไลน์เพื่อส่งเฉพาะข้อความ.
- แตะไอคอนลำโพงเพื่ออ่านคำแปล (ขึ้นกับ TTS voices ในเครื่อง).
- ทดสอบบันทึก/ประวัติ/สรุป/ส่งออก/overlay/PiP. การเลือกคน 1–4 เป็น manual speaker tagging.

## โครงสร้าง

- `speech/RecognitionService.kt`: ฟังเสียงและแปลใน foreground service.
- `translation/TranslationEngines.kt`: ML Kit และ HTTPS online text AI.
- `data/LocalStore.kt`: SQLite persistence.
- `data/UserSettings.kt`: settings และ Android Keystore.
- `service/SubtitleOverlayService.kt`: subtitle overlay.
- `MainActivity.kt`: Compose UI, presentation, speech playback, export.
- `.github/workflows/android-apk.yml`: build-and-test CI.

## ธุรกิจ / ต้นทุน

ML Kit ทั่วไปไม่ต้องจ่ายค่าการแปลข้อความต่อคำขอ แต่ต้องดาวน์โหลดโมเดลก่อนใช้งาน. บริการ Online AI ที่เลือกอาจคิดค่าบริการตาม API usage. Android/system recognizer อาจเชื่อมต่อผู้ให้บริการ และมีนโยบายของระบบนั้น.

## ทดสอบที่ควรทำเพิ่มเติมก่อนเผยแพร่

- ตรวจใบอนุญาต dependencies, ทดสอบ Android 10/12/14/15, RAM 4/6/8GB.
- ทดสอบ 15/30/60 นาที, interruption/notification/lockscreen, เปลี่ยนภาษา Auto บนอุปกรณ์หลายรุ่น.
- ทดสอบแปลไทย-จีนสำเนียงต่างกัน, การพูดทับกัน, การ reconnect network และ error quota.
- เพิ่ม on-device model เช่น whisper.cpp และ speaker diarization engine ในขั้นถัดไป หากต้องการความสามารถเต็มแบบ Transync.
