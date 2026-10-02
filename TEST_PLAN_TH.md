# แผนทดสอบ TriLingual AI (ผลจริงและสิ่งที่ยังไม่ทดสอบ)

## สถานะตรวจสอบ 2 ตุลาคม 2569

| Test ID | เงื่อนไข | ผล | หมายเหตุ |
|---|---|---|---|
| SRC-01 | Python ตรวจโครงสร้างและ parse AndroidManifest.xml | PASS | `python tools/check_project.py` |
| KOT-01 | คอมไพล์ Kotlin ตัวแยกภาษาและทดสอบ 4 กรณี | PASS | ทดสอบไวยากรณ์และ logic เฉพาะไฟล์ `Language.kt` ด้วย `kotlinc` |
| CI-01 | GitHub Actions สั่ง `:app:testDebugUnitTest :app:assembleDebug` | CONFIGURED / NOT RUN | ไม่มี SDK และ Gradle ในสภาพแวดล้อมสร้างไฟล์ |
| APK-01 | ดาวน์โหลด APK และติดตั้งบน Android 10–15 | NOT RUN | ต้องมี artifact จาก Actions ที่ build ผ่านจริง |
| STT-01 | Thai/English/Chinese การพูดสลับประโยค | NOT RUN | ต้องทดสอบโทรศัพท์แต่ละรุ่น |
| STT-02 | Airplane Mode + on-device recognition + language packs | NOT RUN | ไม่การันตีรองรับภาษาทั้งสาม |
| STT-03 | หมุนหน้าจอ, ล็อกหน้าจอ, เปิด 30–60 นาที | NOT RUN | ต้องวัด service restart และบันทึกข้อมูล |
| TRAN-01 | ดาวน์โหลดโมเดลและแปล 6 ทิศทางใน Airplane Mode | NOT RUN | แพ็กโมเดล ML Kit ต้องพร้อม |
| TRAN-02 | Online API, timeout, invalid key, quota error | NOT RUN | ต้องใช้บัญชี API ผู้ใช้เอง |
| TTS-01 | อ่านเสียงสามภาษา | NOT RUN | ขึ้นอยู่กับชุดเสียง TTS ในเครื่อง |
| MEET-01 | บันทึกหลายช่วงและส่งออก Markdown | NOT RUN | ควรตรวจเปิดไฟล์ UTF-8 |
| PIP-01 | เข้าสู่ Picture-in-Picture แล้วอ่านข้อความ | NOT RUN | ทดสอบ Android 10–15 |
| OVER-01 | ขอ permission ‘แสดงทับแอปอื่น’ และปิดได้ | NOT RUN | ทดสอบบน One UI / Pixel / Xiaomi |
| SPEAKER-01 | เปลี่ยนตัวเลือกผู้พูด 1–4 | NOT RUN | manual label เท่านั้น |
| SPEAKER-02 | แยกผู้พูดอัตโนมัติจากไฟล์เสียง | NOT IMPLEMENTED | ต้องเพิ่ม engine diarization |
| PRESENT-02 | ส่งคำบรรยาย live ไปเครื่องอื่นผ่าน QR | NOT IMPLEMENTED | ต้องเพิ่ม streaming relay/server |

## ปัญหาที่ต้องตรวจเฉพาะกับประสบการณ์เดิม

1. พูดจีน (普通话) แล้วตรวจว่าต้นฉบับยังเป็นตัวจีน ไม่ใช่ถูกถอดเป็นภาษาไทย; ตั้งต้นเลือก 中文 เพื่อแยกสาเหตุ STT จากการแปล.
2. พูดเป็นชุด 10 ประโยคต่อเนื่อง ตรวจ 10 บรรทัดใน SQLite; อาจได้จำนวน phrase ไม่ตรง 10 เพราะระบบ STT กำหนดจุดแบ่งช่วงเสียงเอง.
3. พูดเสียงยาว >30 วินาที ตรวจ partial/final ไม่หาย; SpeechRecognizer ของระบบอาจจำกัดระยะความยาวเอง.
4. หมุนแนวตั้ง↔แนวนอนขณะรับฟัง; service ต้องไม่ถูกสร้างใหม่ และ meeting ID ต้องไม่เปลี่ยน.
5. ตัดเน็ตขณะกำลังแปลคลาวด์ → offline fallback ต้องทำงานเมื่อโมเดล ML Kit ลงแล้ว.
6. กด Stop ระหว่างรอผลแปล → ข้อความต้นฉบับที่ commit แล้วต้องยังอยู่ในฐานข้อมูล; translation job ต้องไม่ถูกยกเลิกด้วย service.
7. คืนค่าแอปจาก background ตรวจว่าข้อความประโยคสุดท้ายยังอยู่ในประวัติ; ถ้าระบบฆ่า process ต้องเริ่มจับเสียงใหม่.
8. ตรวจว่าไม่ส่งเสียงไป cloud ในโหมด on-device และไม่ส่งข้อความออนไลน์เมื่อสวิตช์ online AI ปิดอยู่ (network logger/test proxy).

**คำนิยาม PASS อย่างเคร่งครัด:** PASS เฉพาะรายการที่รันได้จริงในสภาพแวดล้อมที่สร้างไฟล์ ไม่มีการสมมุติว่า APK สร้างได้แล้ว.
