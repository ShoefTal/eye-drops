# טיפות – Eye drops reminder

אפליקציית אנדרואיד לתזכורת טיפות עיניים. כל משתמש מגדיר את התרופות שלו:
לכל תרופה שלבים (מתאריך, כמה ימים, באילו שעות), עין אחת או שתיים.

- התראה בכל מועד, קולית (צלצול לבחירה) או שקטה, עם "בוצע" ו"עוד 10 דקות" בתוך ההתראה
- תזכורת חוזרת אם לא סומן, ובדיקת התראה בזמן לבחירה
- שליחת התוכנית לטלפון אחר בוואטסאפ
- עדכון עצמי מגרסת ה-Release האחרונה

## Build
Every push to `main` builds a signed APK and publishes it, with `version.json`, as a GitHub Release.
The signing key is committed encrypted (`keystore/eyedrops.jks.enc`); the build needs the
repository secret `KEYSTORE_PASSWORD` to unlock it.
