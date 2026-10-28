# Toma – medication reminders

אפליקציית אנדרואיד לתזכורת תרופות: טיפות, כדורים, זריקות (עם החלפת מקום), מדבקות, משחות, משאפים, סירופ ומדידות.
כל משתמש בונה תוכנית: לכל תרופה שלבים (מתאריך, כמה ימים, באילו שעות) או "לפי הצורך" עם מרווח מינימלי.

- התראה בכל מועד, קולית (צלצול לבחירה) או שקטה, עם "בוצע" ו"עוד 10 דקות" בתוך ההתראה
- תזכורת חוזרת אם לא סומן, ובדיקת התראה בזמן לבחירה
- מלאי עם התראת חידוש, רישום מדידות והיסטוריה
- שליחת התוכנית לטלפון אחר בוואטסאפ
- עדכון עצמי מגרסת ה-Release האחרונה

## Build
Every push to `main` builds a signed APK and publishes it, with `version.json`, as a GitHub Release.
The signing key is committed encrypted (`keystore/eyedrops.jks.enc`); the build needs the
repository secret `KEYSTORE_PASSWORD` to unlock it.
