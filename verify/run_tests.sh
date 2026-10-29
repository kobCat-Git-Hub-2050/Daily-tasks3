#!/bin/bash
# اختبارات منطق التنبيهات. تحتاج JDK 8 أو أحدث فقط (لا تحتاج Android SDK).
#   bash verify/run_tests.sh
# الاختبار الأول: حساب المواعيد (ReminderPlanner) على JVM مباشرة.
# الاختبار الثاني: محاكاة كاملة لدورة المنبّه (الضبط ← الانطلاق ← ضبط التالي ← إعادة التشغيل...)
#                  على شيفرة AlarmScheduler/AlarmReceiver/BootReceiver/Notifier الحقيقية،
#                  فوق مجسّمات (stubs) مبسّطة لواجهات أندرويد تسجّل المنبّهات والإشعارات.
set -e
cd "$(dirname "$0")"
JAVAC=${JAVAC:-javac}
SRC=../app/src/main/java/com/taqwimi/mahaam
rm -rf out && mkdir -p out/planner out/flow

echo "=== 1) منطق المواعيد ==="
$JAVAC -encoding UTF-8 -nowarn -d out/planner $SRC/ReminderPlanner.java PlannerTest.java 2>/dev/null
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out/planner com.taqwimi.mahaam.PlannerTest

echo
echo "=== 2) محاكاة دورة المنبّه كاملة ==="
$JAVAC -encoding UTF-8 -nowarn -d out/flow $(find android_stubs -name '*.java') \
  $SRC/ReminderPlanner.java $SRC/AlarmScheduler.java $SRC/AlarmReceiver.java \
  $SRC/BootReceiver.java $SRC/Notifier.java AlarmFlowTest.java 2>/dev/null
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out/flow com.taqwimi.mahaam.AlarmFlowTest
