# Chotu v1: आपका पर्सनल फ़ोन असिस्टेंट (Android)

बोलकर (हिंदी/English/Hinglish) फ़ोन चलाइए: ऐप खोलना, कॉल डायल, WhatsApp/ईमेल/LinkedIn के ड्राफ्ट, मैसेज और स्क्रीन पढ़ना।
**जोखिम वाला हर काम (Send, Post, Call बटन, पेमेंट, डिलीट) आप खुद दबाते हैं।**

## 1. APK बनाइए (Android Studio नहीं चाहिए)

1. GitHub पर नई **private** repo बनाइए, जैसे `jarvis`।
2. इस फ़ोल्डर की सारी फ़ाइलें (छिपा हुआ `.github` फ़ोल्डर भी) उसमें push कीजिए:
   ```
   cd jarvis
   git init && git add . && git commit -m "Chotu v1"
   git branch -M main
   git remote add origin https://github.com/<आपका-यूज़रनेम>/jarvis.git
   git push -u origin main
   ```
3. repo में **Actions** टैब खोलिए। “Build Chotu APK” अपने आप चलेगा (लगभग 4-6 मिनट)। न चले तो **Run workflow** दबाइए।
4. पूरा होने पर उसी रन के नीचे **Artifacts → jarvis-apk** डाउनलोड कीजिए, ज़िप खोलकर `app-debug.apk` निकालिए।
5. APK फ़ोन में भेजिए (Drive/USB/WhatsApp), खोलकर इंस्टॉल कीजिए। “Install unknown apps” की अनुमति माँगे तो दे दीजिए।

## 2. पहली बार सेटअप (2 मिनट)

1. Chotu खोलिए। फ़िंगरप्रिंट/PIN माँगेगा (आपके फ़ोन का स्क्रीन लॉक)।
2. सेटिंग्स (⚙) में **Claude API key** डालिए ([console.anthropic.com](https://console.anthropic.com) से), फिर **सेव**।
3. **Accessibility** चालू कीजिए: ⚙ → Accessibility बटन → Chotu → चालू।
4. **Notification access** चालू कीजिए: ⚙ → Notification बटन → Chotu → चालू।
5. माइक और Contacts की अनुमति “Allow” कीजिए।

**अगर टॉगल ग्रे है (Android 13+ पर साइडलोड ऐप में आम है):**
फ़ोन Settings → Apps → Chotu → ऊपर दाएँ **⋮** → **Allow restricted settings** → फिर Accessibility/Notification दोबारा चालू कीजिए।

## 3. कैसे बोलें (उदाहरण)

- “शुभम को WhatsApp पर लिखो कि कल की रिपोर्ट आज शाम तक भेज दे।”
- “रोहन को कॉल करो।”
- “मेरी नोटिफ़िकेशन पढ़ो” / “WhatsApp पर क्या आया है?”
- “Gmail खोलो और बताओ स्क्रीन पर क्या है।”
- “Draft an email to … about the CNC machine quote.”
- “LinkedIn पर आज की पोस्ट का ड्राफ्ट बनाओ, टॉपिक: …”

माइक बटन 🎤 दबाइए, बोलिए। **हि/EN** बटन से सुनने की भाषा बदलती है। टाइप करके भी आदेश दे सकते हैं।

## 3.0a What's new in v1.3

- **Wake phrase is now "Hello Chotu"** and it is detected **offline** on the phone (Vosk small Hindi model). Nothing is sent anywhere until you say it.
- **Music and movies no longer stutter.** The background listener does not take audio focus and does not restart every few seconds.
- **Conversation mode.** After "Hello Chotu" it greets you ("हाय देव, मैं आपकी क्या मदद कर सकता हूँ?") and keeps listening after every answer, with no tapping. It ends only when you say "Chotu close", "बंद हो जा", "bye", "बस" or similar. Commands like "टॉर्च बंद कर दो" do not end it.
- While a conversation is on, music pauses; it resumes when you say "Chotu close".
- **Sci-fi HUD avatar** (rotating rings, voice bars, radar while thinking, boot-up animation). During a conversation it floats over any app (needs Accessibility ON), and touches pass through it.
- Optional auto-sleep after N silent minutes (Settings; 0 = never).
- The build now downloads the Vosk model automatically (GitHub Actions). The APK is bigger (~70-80 MB).
- Android always shows the green microphone dot while any app is listening. No app can hide it.

## 3.0 What's new in v1.2

- The app is now **Chotu** (app name, notifications, settings).
- The wake word is **on by default**. Say "छोटू" and Chotu answers "हाय देव, मैं आपकी क्या मदद कर सकता हूँ?" You don't need to open the app or tap the mic, even while the app is open. Set your name in ⚙.
- New voice commands: torch, volume, ringer mode, brightness (one-time permission), alarm, timer, calendar event draft, SMS draft, music play/pause/next/previous, camera, navigation, web search, battery status, settings pages, notifications panel, quick settings, screenshot, lock screen, power menu. Wi-Fi, Bluetooth, mobile data and Do Not Disturb toggles work by opening quick settings and tapping the tile.
- Chotu also answers general questions by voice. It has no live internet data, so for news, prices or weather it offers a web search.
- After a phone restart, Chotu tries to start itself. If Android blocks that, open the app once.
- A fixed signing key is included, so future updates install over the previous version.
  **First upgrade from v1.1: uninstall the old app once** (you will enter the API key again).

**How to update the repo:** open your GitHub repo, then Add file → Upload files. Drag in the project contents (the hidden `.github` folder is not needed, it is already there), including `app/debug.keystore`. Commit. Wait for the Actions build, then download the `jarvis-apk` artifact.

## 3.1 वेक-वर्ड: “छोटू” बोलकर जगाइए

**चालू करना:** ⚙ → “वेक-वर्ड चालू” स्विच → सेव। साथ ही “बैटरी ऑप्टिमाइज़ेशन से छूट” बटन से Chotu को “Don’t optimize/Unrestricted” कर दीजिए, वरना फ़ोन इसे बंद कर देगा। जगाने का शब्द उसी जगह बदल सकते हैं।

**कैसे चलता है:**
1. फ़ोन जेब/टेबल पर है, आप बोलते हैं: **“छोटू”**।
2. स्क्रीन जाग जाती है। फ़ोन लॉक है तो Chotu बोलता है “फ़ोन अनलॉक कीजिए”, आप फ़िंगरप्रिंट/PIN खुद लगाते हैं।
3. अनलॉक होते ही “बोलिए”, फिर आप आदेश देते हैं। जवाब बोलकर आता है और वह फिर “छोटू” का इंतज़ार करता है।
4. फ़ोन पहले से अनलॉक हो तो एक साँस में भी बोल सकते हैं: “छोटू, शुभम को कॉल करो।”

**सुरक्षा का नियम:** फ़ोन लॉक होते हुए Chotu कोई आदेश नहीं चलाता। लॉक में कोई बाहर का आदमी “छोटू…” बोले तो भी सिर्फ़ स्क्रीन जागेगी, काम नहीं होगा।

**बंद करना:** नोटिफ़िकेशन में “बंद करें”, या ⚙ में स्विच बंद।

**यह ज़रूर जान लीजिए:**
- वेक-वर्ड चालू रहते समय फ़ोन लगातार सुनता है। इसके लिए Google की speech सेवा इस्तेमाल होती है, यानी माइक की आवाज़ Google को जाती रहती है (और इंटरनेट/बैटरी ज़्यादा लगते हैं)। जब ज़रूरत न हो, बंद रखिए।
- कुछ फ़ोन में हर बार सुनना शुरू होने पर बीप आ सकती है। ऐसा हो तो बताइए, मैं उपाय जोड़ दूँगा।
- “छोटू” जैसे शब्द कभी-कभी गलत पहचाने जाते हैं, या शोर में छूट सकते हैं।
- फ़ोन रीस्टार्ट के बाद Android खुद सेवा शुरू नहीं करने देता: Chotu ऐप एक बार खोल लीजिए।
- ऐप खुला हो तब माइक ऐप के पास रहता है, वेक-वर्ड तब रुका रहता है।

## 4. सुरक्षा

- API key फ़ोन के Keystore में एन्क्रिप्टेड रहती है, कोड या GitHub में कहीं नहीं।
- ऐप हर बार फ़िंगरप्रिंट/PIN से खुलता है (60 सेकंड बाहर रहने पर दोबारा लॉक)। रिसेंट्स में बातचीत नहीं दिखती।
- पासवर्ड फ़ील्ड कभी नहीं पढ़े या भरे जाते।
- Send/Post/Pay/Delete/Order/Confirm जैसे बटन Chotu टैप नहीं कर सकता, कोड में ही ब्लॉक हैं।
- स्क्रीन, नोटिफ़िकेशन और कॉन्टैक्ट से मिला टेक्स्ट Claude को “डेटा” की तरह दिया जाता है, आदेश की तरह नहीं। इससे किसी मैसेज में छिपे निर्देश से Chotu हाइजैक होने का खतरा घटता है।
- नोटिफ़िकेशन सिर्फ़ फ़ोन की RAM में रहती हैं।
- ध्यान रहे: जब आप आदेश देते हैं, तब स्क्रीन/नोटिफ़िकेशन का वह हिस्सा जो काम के लिए ज़रूरी हो, Claude API को भेजा जाता है।

## 5. खर्च

हर आदेश पर Claude API के टोकन लगते हैं (स्क्रीन पढ़ने वाले आदेश थोड़े महँगे)। डिफ़ॉल्ट मॉडल `claude-sonnet-5-5` है। सस्ता चलाना हो तो सेटिंग्स में मॉडल `claude-haiku-5-5` कर दीजिए।

## 6. सीमाएँ (v1)

- **WhatsApp/ईमेल/LinkedIn** में ड्राफ्ट भरकर स्क्रीन खुलती है, भेजना आपको होता है। WhatsApp के लिए नाम आपके Contacts में होना चाहिए (या नंबर बोलिए)।
- **कॉल** डायलर में नंबर भर देता है, हरा बटन आप दबाते हैं।
- Android कभी-कभी बैकग्राउंड से एक के बाद एक कई ऐप खोलने से रोक देता है। एक आदेश में एक ऐप सबसे भरोसेमंद है।
- बैंकिंग जैसे कुछ ऐप स्क्रीन पढ़ने देते ही नहीं।
- हिंदी बोलने के लिए फ़ोन में Hindi text-to-speech आवाज़ चाहिए (Settings → Text-to-speech)।
- बातचीत की याद सिर्फ़ ऐप खुले रहने तक है।

## 7. v2 में क्या जोड़ सकते हैं

- दूसरे ऐप्स के ऊपर तैरता ऑर्ब बबल (हर जगह से बुलाने के लिए)
- ऑफ़लाइन वेक-वर्ड इंजन (जैसे Porcupine), ताकि बैटरी और डेटा कम लगे और गलत-जागरण घटें
- Gmail पढ़ना/सारांश, Calendar, रिमाइंडर
- कार्टून चेहरे वाला avatar (आपके रील्स वाला)
- आपके MachineStation ऐप्स (IT Help Desk, Sales Dashboard) से कनेक्शन

## बिल्ड में दिक्कत आए तो

Actions टैब में लाल रन खोलकर एरर की पंक्तियाँ कॉपी करके मुझे भेज दीजिए, मैं ठीक कर दूँगा।
