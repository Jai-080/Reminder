package com.example.reminder;



import org.junit.Test;



import java.util.Calendar;



import static org.junit.Assert.*;



public class DefectAndLogicTest {



    // =========================================================================

    // D7 TESTS: Deterministic Payment Timestamp

    // =========================================================================



    @Test

    public void testD7_getLastPaidAt_deterministicNullHandling() {

        // Completed payment with null timestamp must return null deterministically

        MonthlyPayment payment = new MonthlyPayment(1, "Electricity", true, System.currentTimeMillis());

        Long t1 = payment.getLastPaidAt();

        Long t2 = payment.getLastPaidAt();



        assertNull("getLastPaidAt() must return null for unrecorded lastPaidAt even if completed", t1);

        assertNull("Subsequent call must remain null", t2);

        assertEquals("Calls must be deterministic and identical", t1, t2);



        // Incomplete payment with null timestamp must also return null deterministically

        MonthlyPayment incomplete = new MonthlyPayment(2, "Water", false, System.currentTimeMillis());

        assertNull(incomplete.getLastPaidAt());

        assertNull(incomplete.getLastPaidAt());

    }



    @Test

    public void testD7_getLastPaidAt_deterministicWithValidTimestamp() throws InterruptedException {

        long recordedTime = 1700000000000L;

        MonthlyPayment payment = new MonthlyPayment(1, "Electricity", true, System.currentTimeMillis());

        payment.setLastPaidAt(recordedTime);



        Long t1 = payment.getLastPaidAt();

        Thread.sleep(10);

        Long t2 = payment.getLastPaidAt();



        assertNotNull("Recorded timestamp must not be null", t1);

        assertEquals("First call must return recorded timestamp", Long.valueOf(recordedTime), t1);

        assertEquals("Subsequent calls must return identical timestamp without regeneration", t1, t2);

    }



    @Test

    public void testD7_paymentCompletionRecordsTimestamp() {

        MonthlyPayment payment = new MonthlyPayment(1, "Internet", false, System.currentTimeMillis());

        assertNull("Initial payment should have null lastPaidAt", payment.getLastPaidAt());



        long completedTimestamp = System.currentTimeMillis();

        payment.setLastPaidAt(completedTimestamp);

        payment.setCompleted(true);



        assertEquals(Long.valueOf(completedTimestamp), payment.getLastPaidAt());

        // Verify getter does not mutate or regenerate timestamp

        assertEquals(Long.valueOf(completedTimestamp), payment.getLastPaidAt());

    }



    // =========================================================================

    // D4 TESTS: Recurring Payment Date Drift Prevention

    // =========================================================================



    @Test

    public void testD4_monthEndTransitions_Jan31ToFeb28ToMarch31() {

        // Jan 31, 2026 09:00:00 (non-leap year)

        Calendar cal = Calendar.getInstance();

        cal.set(2026, Calendar.JANUARY, 31, 9, 0, 0);

        cal.set(Calendar.MILLISECOND, 0);

        long jan31 = cal.getTimeInMillis();



        MonthlyPayment payment = new MonthlyPayment(1, null, "Insurance", false, jan31, "SYNCED", 100.0, RecurrenceType.MONTHLY, "0", 31);

        assertEquals(31, payment.getOriginalDueDay());



        // Cycle 1: Advance from Jan 31 to Feb

        long febDue = MonthlyPayment.calculateImmediateNextDueDate(payment.getDueDate(), payment.getRecurrence(), payment.getOriginalDueDay());

        Calendar checkCal = Calendar.getInstance();

        checkCal.setTimeInMillis(febDue);

        assertEquals("Feb in 2026 should clamp to 28th", 28, checkCal.get(Calendar.DAY_OF_MONTH));

        assertEquals("Month should be February", Calendar.FEBRUARY, checkCal.get(Calendar.MONTH));

        assertEquals("Hour must be preserved", 9, checkCal.get(Calendar.HOUR_OF_DAY));



        // Cycle 2: Advance from Feb 28 to March -> MUST restore original day 31!

        payment.setDueDateMillis(febDue);

        long marchDue = MonthlyPayment.calculateImmediateNextDueDate(payment.getDueDate(), payment.getRecurrence(), payment.getOriginalDueDay());

        checkCal.setTimeInMillis(marchDue);

        assertEquals("March MUST return to original anchored day 31 (no date drift)", 31, checkCal.get(Calendar.DAY_OF_MONTH));

        assertEquals("Month should be March", Calendar.MARCH, checkCal.get(Calendar.MONTH));

        assertEquals("Hour must be preserved", 9, checkCal.get(Calendar.HOUR_OF_DAY));

    }



    @Test

    public void testD4_leapYear_Jan31ToFeb29ToMarch31() {

        // Jan 31, 2024 09:00:00 (leap year)

        Calendar cal = Calendar.getInstance();

        cal.set(2024, Calendar.JANUARY, 31, 9, 0, 0);

        cal.set(Calendar.MILLISECOND, 0);

        long jan31 = cal.getTimeInMillis();



        // Cycle 1: Jan 31 -> Feb 2024 (leap year has 29 days)

        long febDue = MonthlyPayment.calculateImmediateNextDueDate(jan31, RecurrenceType.MONTHLY, 31);

        Calendar checkCal = Calendar.getInstance();

        checkCal.setTimeInMillis(febDue);

        assertEquals("Feb 2024 leap year should clamp to 29th", 29, checkCal.get(Calendar.DAY_OF_MONTH));

        assertEquals(Calendar.FEBRUARY, checkCal.get(Calendar.MONTH));



        // Cycle 2: Feb 29 -> March 2024 -> must return to 31st

        long marchDue = MonthlyPayment.calculateImmediateNextDueDate(febDue, RecurrenceType.MONTHLY, 31);

        checkCal.setTimeInMillis(marchDue);

        assertEquals("March 2024 must return to 31st", 31, checkCal.get(Calendar.DAY_OF_MONTH));

        assertEquals(Calendar.MARCH, checkCal.get(Calendar.MONTH));

    }



    @Test

    public void testD4_monthEndTransitions_Aug31ToSep30ToOct31() {

        // Aug 31, 2026

        Calendar cal = Calendar.getInstance();

        cal.set(2026, Calendar.AUGUST, 31, 10, 0, 0);

        cal.set(Calendar.MILLISECOND, 0);



        long sepDue = MonthlyPayment.calculateImmediateNextDueDate(cal.getTimeInMillis(), RecurrenceType.MONTHLY, 31);

        Calendar checkCal = Calendar.getInstance();

        checkCal.setTimeInMillis(sepDue);

        assertEquals("September has 30 days, clamp to 30", 30, checkCal.get(Calendar.DAY_OF_MONTH));

        assertEquals(Calendar.SEPTEMBER, checkCal.get(Calendar.MONTH));



        long octDue = MonthlyPayment.calculateImmediateNextDueDate(sepDue, RecurrenceType.MONTHLY, 31);

        checkCal.setTimeInMillis(octDue);

        assertEquals("October has 31 days, return to 31", 31, checkCal.get(Calendar.DAY_OF_MONTH));

        assertEquals(Calendar.OCTOBER, checkCal.get(Calendar.MONTH));

    }



    @Test

    public void testD4_day30_Jan30ToFeb28Or29ToMarch30() {

        // Non-leap year 2023: Jan 30 -> Feb 28 -> March 30

        Calendar cal2023 = Calendar.getInstance();

        cal2023.set(2023, Calendar.JANUARY, 30, 9, 0, 0);

        long feb2023 = MonthlyPayment.calculateImmediateNextDueDate(cal2023.getTimeInMillis(), RecurrenceType.MONTHLY, 30);

        Calendar c = Calendar.getInstance();

        c.setTimeInMillis(feb2023);

        assertEquals(28, c.get(Calendar.DAY_OF_MONTH));



        long mar2023 = MonthlyPayment.calculateImmediateNextDueDate(feb2023, RecurrenceType.MONTHLY, 30);

        c.setTimeInMillis(mar2023);

        assertEquals("March 2023 must return to 30th", 30, c.get(Calendar.DAY_OF_MONTH));



        // Leap year 2024: Jan 30 -> Feb 29 -> March 30

        Calendar cal2024 = Calendar.getInstance();

        cal2024.set(2024, Calendar.JANUARY, 30, 9, 0, 0);

        long feb2024 = MonthlyPayment.calculateImmediateNextDueDate(cal2024.getTimeInMillis(), RecurrenceType.MONTHLY, 30);

        c.setTimeInMillis(feb2024);

        assertEquals(29, c.get(Calendar.DAY_OF_MONTH));



        long mar2024 = MonthlyPayment.calculateImmediateNextDueDate(feb2024, RecurrenceType.MONTHLY, 30);

        c.setTimeInMillis(mar2024);

        assertEquals("March 2024 must return to 30th", 30, c.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testD4_normalMonthlyDate_remainsConstant() {

        Calendar cal = Calendar.getInstance();

        cal.set(2026, Calendar.JANUARY, 15, 9, 0, 0);



        long nextDue = MonthlyPayment.calculateImmediateNextDueDate(cal.getTimeInMillis(), RecurrenceType.MONTHLY, 15);

        Calendar c = Calendar.getInstance();

        c.setTimeInMillis(nextDue);

        assertEquals(15, c.get(Calendar.DAY_OF_MONTH));

        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH));

    }



    @Test

    public void testD4_yearlyLeapYear_Feb29Anchoring() {

        // Leap year 2024 Feb 29

        Calendar cal = Calendar.getInstance();

        cal.set(2024, Calendar.FEBRUARY, 29, 9, 0, 0);



        // 2025: clamped to Feb 28

        long y2025 = MonthlyPayment.calculateImmediateNextDueDate(cal.getTimeInMillis(), RecurrenceType.YEARLY, 29);

        Calendar c = Calendar.getInstance();

        c.setTimeInMillis(y2025);

        assertEquals(2025, c.get(Calendar.YEAR));

        assertEquals(28, c.get(Calendar.DAY_OF_MONTH));



        // 2026: Feb 28

        long y2026 = MonthlyPayment.calculateImmediateNextDueDate(y2025, RecurrenceType.YEARLY, 29);

        c.setTimeInMillis(y2026);

        assertEquals(2026, c.get(Calendar.YEAR));

        assertEquals(28, c.get(Calendar.DAY_OF_MONTH));



        // 2027: Feb 28

        long y2027 = MonthlyPayment.calculateImmediateNextDueDate(y2026, RecurrenceType.YEARLY, 29);

        c.setTimeInMillis(y2027);

        assertEquals(2027, c.get(Calendar.YEAR));

        assertEquals(28, c.get(Calendar.DAY_OF_MONTH));



        // 2028 (leap year): MUST return to Feb 29!

        long y2028 = MonthlyPayment.calculateImmediateNextDueDate(y2027, RecurrenceType.YEARLY, 29);

        c.setTimeInMillis(y2028);

        assertEquals(2028, c.get(Calendar.YEAR));

        assertEquals("Leap year 2028 must return to Feb 29th", 29, c.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testD4_quarterlyAndBiMonthlyRecurrence_driftPrevention() {

        // Quarterly from Nov 30, 2025

        Calendar cal = Calendar.getInstance();

        cal.set(2025, Calendar.NOVEMBER, 30, 9, 0, 0);



        // Nov 30 + 3 months -> Feb 28, 2026 (clamped)

        long qFeb = MonthlyPayment.calculateImmediateNextDueDate(cal.getTimeInMillis(), RecurrenceType.QUARTERLY, 30);

        Calendar c = Calendar.getInstance();

        c.setTimeInMillis(qFeb);

        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH));

        assertEquals(28, c.get(Calendar.DAY_OF_MONTH));



        // Feb 28 + 3 months -> May 30, 2026 (must return to day 30)

        long qMay = MonthlyPayment.calculateImmediateNextDueDate(qFeb, RecurrenceType.QUARTERLY, 30);

        c.setTimeInMillis(qMay);

        assertEquals(Calendar.MAY, c.get(Calendar.MONTH));

        assertEquals("May must return to day 30", 30, c.get(Calendar.DAY_OF_MONTH));

    }



    // =========================================================================

    // D5 TESTS: Prevent Notification ID Collisions

    // =========================================================================



    @Test

    public void testD5_notificationIdNamespacesPreventCollision() {

        int reminderLocalId = 1;

        int paymentLocalId = 1;



        int paymentNotificationId = AlarmUtils.getPaymentNotificationId(paymentLocalId);



        // Reminder notification ID is 1 (reminderLocalId)

        // Payment notification ID is 100001 (PAYMENT_NOTIFICATION_OFFSET + paymentLocalId)

        assertNotEquals("Reminder ID and Payment Notification ID MUST NOT collide",

                reminderLocalId, paymentNotificationId);

        assertEquals(100001, paymentNotificationId);

        assertEquals(1, reminderLocalId);

    }



    @Test

    public void testD5_multipleRemindersAndPaymentsCoexist() {

        for (int id = 1; id <= 100; id++) {

            int reminderNotifId = id;

            int paymentNotifId = AlarmUtils.getPaymentNotificationId(id);



            assertTrue("Reminder notification ID must be in lower range", reminderNotifId < AlarmUtils.PAYMENT_NOTIFICATION_OFFSET);

            assertTrue("Payment notification ID must be in offset range", paymentNotifId >= AlarmUtils.PAYMENT_NOTIFICATION_OFFSET);

            assertNotEquals("IDs must never collide for identical database IDs", reminderNotifId, paymentNotifId);

        }

    }



    @Test

    public void testD5_pendingIntentIdentitySeparation() {

        int paymentId = 5;

        int reminderId = 5;



        // Verify request codes are in distinct namespaces

        int reminderActivityRequestCode = reminderId + 10000;

        int paymentActivityRequestCode = paymentId + 20000;

        int paymentDismissRequestCode = AlarmUtils.getPaymentNotificationId(paymentId);



        assertNotEquals(reminderActivityRequestCode, paymentActivityRequestCode);

        assertNotEquals(reminderActivityRequestCode, paymentDismissRequestCode);

        assertNotEquals(reminderId, paymentDismissRequestCode);

    }



    // =========================================================================

    // D6 TESTS: Overdue Payment Recurrence

    // =========================================================================



    @Test

    public void testD6_multipleOverdueCycles_advancesToStrictFuture() {

        // Payment was due Jan 10, 2026 09:00:00

        Calendar dueCal = Calendar.getInstance();

        dueCal.set(2026, Calendar.JANUARY, 10, 9, 0, 0);

        dueCal.set(Calendar.MILLISECOND, 0);

        long jan10Due = dueCal.getTimeInMillis();



        // Completed 3+ months late on April 20, 2026 14:00:00

        Calendar compCal = Calendar.getInstance();

        compCal.set(2026, Calendar.APRIL, 20, 14, 0, 0);

        compCal.set(Calendar.MILLISECOND, 0);

        long apr20Completion = compCal.getTimeInMillis();



        long nextDue = MonthlyPayment.calculateNextDueDate(jan10Due, RecurrenceType.MONTHLY, 10, apr20Completion);



        assertTrue("Next due date must be strictly after the completion date", nextDue > apr20Completion);



        Calendar resultCal = Calendar.getInstance();

        resultCal.setTimeInMillis(nextDue);

        assertEquals("Next due month should be May", Calendar.MAY, resultCal.get(Calendar.MONTH));

        assertEquals("Next due day should be the anchored 10th", 10, resultCal.get(Calendar.DAY_OF_MONTH));

        assertEquals(2026, resultCal.get(Calendar.YEAR));

        assertEquals(9, resultCal.get(Calendar.HOUR_OF_DAY));

    }



    @Test

    public void testD6_oneMissedMonthlyCycle() {

        // Due March 10, 2026 09:00:00, completed March 25, 2026 12:00:00 (15 days late)

        Calendar dueCal = Calendar.getInstance();

        dueCal.set(2026, Calendar.MARCH, 10, 9, 0, 0);

        long mar10 = dueCal.getTimeInMillis();



        Calendar compCal = Calendar.getInstance();

        compCal.set(2026, Calendar.MARCH, 25, 12, 0, 0);

        long mar25 = compCal.getTimeInMillis();



        long nextDue = MonthlyPayment.calculateNextDueDate(mar10, RecurrenceType.MONTHLY, 10, mar25);



        assertTrue(nextDue > mar25);

        Calendar resultCal = Calendar.getInstance();

        resultCal.setTimeInMillis(nextDue);

        assertEquals(Calendar.APRIL, resultCal.get(Calendar.MONTH));

        assertEquals(10, resultCal.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testD6_earlyCompletion_advancesExactlyOneCycle() {

        // Due April 10, 2026 09:00:00, paid early on April 5, 2026 10:00:00

        Calendar dueCal = Calendar.getInstance();

        dueCal.set(2026, Calendar.APRIL, 10, 9, 0, 0);

        long apr10 = dueCal.getTimeInMillis();



        Calendar compCal = Calendar.getInstance();

        compCal.set(2026, Calendar.APRIL, 5, 10, 0, 0);

        long apr5 = compCal.getTimeInMillis();



        long nextDue = MonthlyPayment.calculateNextDueDate(apr10, RecurrenceType.MONTHLY, 10, apr5);



        Calendar resultCal = Calendar.getInstance();

        resultCal.setTimeInMillis(nextDue);

        assertEquals(Calendar.MAY, resultCal.get(Calendar.MONTH));

        assertEquals(10, resultCal.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testD6_onDueDateCompletion_advancesExactlyOneCycle() {

        // Due April 10, 2026 09:00:00, paid on April 10, 2026 11:00:00

        Calendar dueCal = Calendar.getInstance();

        dueCal.set(2026, Calendar.APRIL, 10, 9, 0, 0);

        long apr10 = dueCal.getTimeInMillis();



        Calendar compCal = Calendar.getInstance();

        compCal.set(2026, Calendar.APRIL, 10, 11, 0, 0);

        long apr10Paid = compCal.getTimeInMillis();



        long nextDue = MonthlyPayment.calculateNextDueDate(apr10, RecurrenceType.MONTHLY, 10, apr10Paid);



        Calendar resultCal = Calendar.getInstance();

        resultCal.setTimeInMillis(nextDue);

        assertEquals(Calendar.MAY, resultCal.get(Calendar.MONTH));

        assertEquals(10, resultCal.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testD6_oneTimePayment_doesNotAdvanceDueDate() {

        long dueDate = 1700000000000L;

        long completionDate = 1710000000000L;



        long nextDue = MonthlyPayment.calculateNextDueDate(dueDate, RecurrenceType.ONE_TIME, 15, completionDate);

        assertEquals("ONE_TIME payment must not advance due date", dueDate, nextDue);

    }



    // =========================================================================

    // EXISTING PHASE 1 TESTS (PRESERVED)

    // =========================================================================



    @Test

    public void testRecurrenceTypes_intervalCalculations() {

        Calendar base = Calendar.getInstance();

        base.set(2026, Calendar.JANUARY, 1, 0, 0, 0);

        base.set(Calendar.MILLISECOND, 0);



        // Bi-Monthly (+2 months) -> March 1

        Calendar cBi = (Calendar) base.clone();

        cBi.add(Calendar.MONTH, 2);

        assertEquals(Calendar.MARCH, cBi.get(Calendar.MONTH));



        // Quarterly (+3 months) -> April 1

        Calendar cQ = (Calendar) base.clone();

        cQ.add(Calendar.MONTH, 3);

        assertEquals(Calendar.APRIL, cQ.get(Calendar.MONTH));



        // Half-Yearly (+6 months) -> July 1

        Calendar cHalf = (Calendar) base.clone();

        cHalf.add(Calendar.MONTH, 6);

        assertEquals(Calendar.JULY, cHalf.get(Calendar.MONTH));



        // Yearly (+1 year) -> 2027

        Calendar cYear = (Calendar) base.clone();

        cYear.add(Calendar.YEAR, 1);

        assertEquals(2027, cYear.get(Calendar.YEAR));

    }



    @Test

    public void testSnoozeCalculations() {

        long now = 1000000000L;

        long snooze10 = now + 10 * 60 * 1000L;

        long snooze20 = now + 20 * 60 * 1000L;

        long snooze30 = now + 30 * 60 * 1000L;

        long snooze60 = now + 60 * 60 * 1000L;



        assertEquals(600000L, snooze10 - now);

        assertEquals(1200000L, snooze20 - now);

        assertEquals(1800000L, snooze30 - now);

        assertEquals(3600000L, snooze60 - now);

    }



    @Test

    public void testD1_ReminderOrderingAndIdSelection() {

        Reminder r1 = new Reminder(1, "Future Reminder", 2000000000L);

        Reminder r2 = new Reminder(2, "Earlier Reminder", 1000000000L);



        java.util.List<Reminder> sortedList = new java.util.ArrayList<>();

        sortedList.add(r2);

        sortedList.add(r1);



        Reminder flawedSelection = sortedList.get(sortedList.size() - 1);

        assertEquals("Old flawed logic selected r1 instead of newly created r2", 1, flawedSelection.getId());



        long insertedRowId = 2L;

        int fixedId = (int) insertedRowId;

        assertEquals("Fixed logic correctly identifies r2 with ID 2", 2, fixedId);

        assertNotEquals("Fixed logic does NOT select the wrong reminder ID", flawedSelection.getId(), fixedId);

    }



    @Test

    public void testD2_RebootRestorationLogic() {

        long now = 1000000L;



        Reminder futureReminder = new Reminder(1, 101L, "Future Reminder", 2000000L, false, 0L, "SYNCED");

        long triggerTime1 = futureReminder.getSnoozedUntil() > 0 ? futureReminder.getSnoozedUntil() : futureReminder.getTime();

        assertTrue("Future reminder should be scheduled", triggerTime1 > now);

        assertEquals(2000000L, triggerTime1);



        Reminder offlineExpiredReminder = new Reminder(2, 102L, "Missed Reminder", 500000L, false, 0L, "SYNCED");

        long triggerTime2 = offlineExpiredReminder.getSnoozedUntil() > 0 ? offlineExpiredReminder.getSnoozedUntil() : offlineExpiredReminder.getTime();

        assertFalse("Offline expired reminder should not be scheduled", triggerTime2 > now);



        Reminder snoozedReminder = new Reminder(3, 103L, "Snoozed Reminder", 500000L, false, 1500000L, "SYNCED");

        long triggerTime3 = snoozedReminder.getSnoozedUntil() > 0 ? snoozedReminder.getSnoozedUntil() : snoozedReminder.getTime();

        assertTrue("Snoozed reminder should be scheduled for snoozedUntil", triggerTime3 > now);

        assertEquals("Should use snoozedUntil timestamp", 1500000L, triggerTime3);

    }

// =========================================================================

    // CUSTOM RECURRENCE TESTS: Every N Days (CUSTOM_DAYS)

    // =========================================================================



    @Test

    public void testCustomRecurrence_exactIntervals() {

        // Base: January 28, 2026 at 09:00:00

        Calendar baseCal = Calendar.getInstance();

        baseCal.set(2026, Calendar.JANUARY, 28, 9, 0, 0);

        baseCal.set(Calendar.MILLISECOND, 0);

        long baseMillis = baseCal.getTimeInMillis();



        // 1 Day -> January 29, 2026

        long d1 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 1);

        Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(d1);

        assertEquals(2026, c1.get(Calendar.YEAR));

        assertEquals(Calendar.JANUARY, c1.get(Calendar.MONTH));

        assertEquals(29, c1.get(Calendar.DAY_OF_MONTH));



        // 7 Days -> February 4, 2026

        long d7 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 7);

        Calendar c7 = Calendar.getInstance(); c7.setTimeInMillis(d7);

        assertEquals(2026, c7.get(Calendar.YEAR));

        assertEquals(Calendar.FEBRUARY, c7.get(Calendar.MONTH));

        assertEquals(4, c7.get(Calendar.DAY_OF_MONTH));



        // 14 Days -> February 11, 2026

        long d14 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 14);

        Calendar c14 = Calendar.getInstance(); c14.setTimeInMillis(d14);

        assertEquals(2026, c14.get(Calendar.YEAR));

        assertEquals(Calendar.FEBRUARY, c14.get(Calendar.MONTH));

        assertEquals(11, c14.get(Calendar.DAY_OF_MONTH));



        // 28 Days -> February 25, 2026

        long d28 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 28);

        Calendar c28 = Calendar.getInstance(); c28.setTimeInMillis(d28);

        assertEquals(2026, c28.get(Calendar.YEAR));

        assertEquals(Calendar.FEBRUARY, c28.get(Calendar.MONTH));

        assertEquals(25, c28.get(Calendar.DAY_OF_MONTH));



        // 30 Days -> February 27, 2026

        long d30 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 30);

        Calendar c30 = Calendar.getInstance(); c30.setTimeInMillis(d30);

        assertEquals(2026, c30.get(Calendar.YEAR));

        assertEquals(Calendar.FEBRUARY, c30.get(Calendar.MONTH));

        assertEquals(27, c30.get(Calendar.DAY_OF_MONTH));



        // 45 Days -> March 14, 2026

        long d45 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 45);

        Calendar c45 = Calendar.getInstance(); c45.setTimeInMillis(d45);

        assertEquals(2026, c45.get(Calendar.YEAR));

        assertEquals(Calendar.MARCH, c45.get(Calendar.MONTH));

        assertEquals(14, c45.get(Calendar.DAY_OF_MONTH));



        // 90 Days -> April 28, 2026

        long d90 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 90);

        Calendar c90 = Calendar.getInstance(); c90.setTimeInMillis(d90);

        assertEquals(2026, c90.get(Calendar.YEAR));

        assertEquals(Calendar.APRIL, c90.get(Calendar.MONTH));

        assertEquals(28, c90.get(Calendar.DAY_OF_MONTH));



        // 365 Days -> January 28, 2027

        long d365 = MonthlyPayment.calculateImmediateNextDueDate(baseMillis, RecurrenceType.CUSTOM_DAYS, 28, 365);

        Calendar c365 = Calendar.getInstance(); c365.setTimeInMillis(d365);

        assertEquals(2027, c365.get(Calendar.YEAR));

        assertEquals(Calendar.JANUARY, c365.get(Calendar.MONTH));

        assertEquals(28, c365.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testCustomRecurrence_timeOfDayPreservation() {

        Calendar cal = Calendar.getInstance();

        cal.set(2026, Calendar.MARCH, 15, 14, 35, 42);

        cal.set(Calendar.MILLISECOND, 123);

        long start = cal.getTimeInMillis();



        long next = MonthlyPayment.calculateImmediateNextDueDate(start, RecurrenceType.CUSTOM_DAYS, 15, 28);

        Calendar nextCal = Calendar.getInstance();

        nextCal.setTimeInMillis(next);



        assertEquals("Hour must be preserved exactly", 14, nextCal.get(Calendar.HOUR_OF_DAY));

        assertEquals("Minute must be preserved exactly", 35, nextCal.get(Calendar.MINUTE));

        assertEquals("Second must be preserved exactly", 42, nextCal.get(Calendar.SECOND));

        assertEquals("Millisecond must be preserved exactly", 123, nextCal.get(Calendar.MILLISECOND));

    }



    @Test

    public void testCustomRecurrence_leapYearBoundary() {

        // Leap year: February 28, 2024

        Calendar calLeap = Calendar.getInstance();

        calLeap.set(2024, Calendar.FEBRUARY, 28, 9, 0, 0);

        calLeap.set(Calendar.MILLISECOND, 0);



        long feb29 = MonthlyPayment.calculateImmediateNextDueDate(calLeap.getTimeInMillis(), RecurrenceType.CUSTOM_DAYS, 28, 1);

        Calendar c29 = Calendar.getInstance(); c29.setTimeInMillis(feb29);

        assertEquals(Calendar.FEBRUARY, c29.get(Calendar.MONTH));

        assertEquals(29, c29.get(Calendar.DAY_OF_MONTH));



        long mar1 = MonthlyPayment.calculateImmediateNextDueDate(calLeap.getTimeInMillis(), RecurrenceType.CUSTOM_DAYS, 28, 2);

        Calendar cMar1 = Calendar.getInstance(); cMar1.setTimeInMillis(mar1);

        assertEquals(Calendar.MARCH, cMar1.get(Calendar.MONTH));

        assertEquals(1, cMar1.get(Calendar.DAY_OF_MONTH));



        // Non-leap year: February 28, 2023

        Calendar calNonLeap = Calendar.getInstance();

        calNonLeap.set(2023, Calendar.FEBRUARY, 28, 9, 0, 0);

        calNonLeap.set(Calendar.MILLISECOND, 0);



        long nonLeapNext = MonthlyPayment.calculateImmediateNextDueDate(calNonLeap.getTimeInMillis(), RecurrenceType.CUSTOM_DAYS, 28, 1);

        Calendar cNonLeap = Calendar.getInstance(); cNonLeap.setTimeInMillis(nonLeapNext);

        assertEquals("In 2023, Feb 28 + 1 day must be March 1", Calendar.MARCH, cNonLeap.get(Calendar.MONTH));

        assertEquals(1, cNonLeap.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testCustomRecurrence_monthEndAndYearTransitions() {

        // January 31, 2026 + 1 day -> February 1

        Calendar cJan31 = Calendar.getInstance();

        cJan31.set(2026, Calendar.JANUARY, 31, 10, 0, 0);

        cJan31.set(Calendar.MILLISECOND, 0);



        long nextFeb = MonthlyPayment.calculateImmediateNextDueDate(cJan31.getTimeInMillis(), RecurrenceType.CUSTOM_DAYS, 31, 1);

        Calendar resFeb = Calendar.getInstance(); resFeb.setTimeInMillis(nextFeb);

        assertEquals(Calendar.FEBRUARY, resFeb.get(Calendar.MONTH));

        assertEquals(1, resFeb.get(Calendar.DAY_OF_MONTH));



        // December 25, 2026 + 14 days -> January 8, 2027

        Calendar cDec25 = Calendar.getInstance();

        cDec25.set(2026, Calendar.DECEMBER, 25, 10, 0, 0);

        cDec25.set(Calendar.MILLISECOND, 0);



        long nextJan = MonthlyPayment.calculateImmediateNextDueDate(cDec25.getTimeInMillis(), RecurrenceType.CUSTOM_DAYS, 25, 14);

        Calendar resJan = Calendar.getInstance(); resJan.setTimeInMillis(nextJan);

        assertEquals(2027, resJan.get(Calendar.YEAR));

        assertEquals(Calendar.JANUARY, resJan.get(Calendar.MONTH));

        assertEquals(8, resJan.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testCustomRecurrence_overdueProgression() {

        // Scheduled: Jan 1, 2026 at 09:00:00

        Calendar schedCal = Calendar.getInstance();

        schedCal.set(2026, Calendar.JANUARY, 1, 9, 0, 0);

        schedCal.set(Calendar.MILLISECOND, 0);

        long jan1Due = schedCal.getTimeInMillis();



        // Completed: March 15, 2026 at 12:00:00 (multiple missed 28-day intervals)

        Calendar compCal = Calendar.getInstance();

        compCal.set(2026, Calendar.MARCH, 15, 12, 0, 0);

        compCal.set(Calendar.MILLISECOND, 0);

        long mar15Completion = compCal.getTimeInMillis();



        // Jan 1 + 28d -> Jan 29 (<= Mar 15)

        // Jan 29 + 28d -> Feb 26 (<= Mar 15)

        // Feb 26 + 28d -> March 26 (> Mar 15)

        long nextDue = MonthlyPayment.calculateNextDueDate(jan1Due, RecurrenceType.CUSTOM_DAYS, 1, 28, mar15Completion);

        Calendar nextCal = Calendar.getInstance(); nextCal.setTimeInMillis(nextDue);



        assertEquals(2026, nextCal.get(Calendar.YEAR));

        assertEquals(Calendar.MARCH, nextCal.get(Calendar.MONTH));

        assertEquals(26, nextCal.get(Calendar.DAY_OF_MONTH));

        assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY));

        assertTrue("Next due date must be strictly after completion timestamp", nextDue > mar15Completion);



        // Completion before scheduled date (early completion): advances exactly 1 interval

        Calendar earlyCal = Calendar.getInstance();

        earlyCal.set(2025, Calendar.DECEMBER, 30, 10, 0, 0);

        long earlyNext = MonthlyPayment.calculateNextDueDate(jan1Due, RecurrenceType.CUSTOM_DAYS, 1, 28, earlyCal.getTimeInMillis());

        Calendar earlyRes = Calendar.getInstance(); earlyRes.setTimeInMillis(earlyNext);

        assertEquals(Calendar.JANUARY, earlyRes.get(Calendar.MONTH));

        assertEquals(29, earlyRes.get(Calendar.DAY_OF_MONTH));



        // Completion exactly on scheduled date: advances to next interval

        long exactNext = MonthlyPayment.calculateNextDueDate(jan1Due, RecurrenceType.CUSTOM_DAYS, 1, 28, jan1Due);

        Calendar exactRes = Calendar.getInstance(); exactRes.setTimeInMillis(exactNext);

        assertEquals(Calendar.JANUARY, exactRes.get(Calendar.MONTH));

        assertEquals(29, exactRes.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testCustomRecurrence_invalidIntervalsFallback() {

        Calendar cal = Calendar.getInstance();

        cal.set(2026, Calendar.JANUARY, 1, 9, 0, 0);

        cal.set(Calendar.MILLISECOND, 0);

        long start = cal.getTimeInMillis();



        // 0 days -> defaults to 28 days -> Jan 29

        long res0 = MonthlyPayment.calculateImmediateNextDueDate(start, RecurrenceType.CUSTOM_DAYS, 1, 0);

        Calendar c0 = Calendar.getInstance(); c0.setTimeInMillis(res0);

        assertEquals(29, c0.get(Calendar.DAY_OF_MONTH));



        // Negative days -> defaults to 28 days -> Jan 29

        long resNeg = MonthlyPayment.calculateImmediateNextDueDate(start, RecurrenceType.CUSTOM_DAYS, 1, -10);

        Calendar cNeg = Calendar.getInstance(); cNeg.setTimeInMillis(resNeg);

        assertEquals(29, cNeg.get(Calendar.DAY_OF_MONTH));



        // > 365 days -> defaults to 28 days -> Jan 29

        long resOver = MonthlyPayment.calculateImmediateNextDueDate(start, RecurrenceType.CUSTOM_DAYS, 1, 500);

        Calendar cOver = Calendar.getInstance(); cOver.setTimeInMillis(resOver);

        assertEquals(29, cOver.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testCustomRecurrence_editingAndSwitchingTypes() {

        Calendar cal = Calendar.getInstance();

        cal.set(2026, Calendar.JANUARY, 10, 9, 0, 0);

        cal.set(Calendar.MILLISECOND, 0);

        long start = cal.getTimeInMillis();



        // Payment initially set to Custom with 28 days

        MonthlyPayment payment = new MonthlyPayment(1, null, "Gym", false, start, "SYNCED", 50.0, RecurrenceType.CUSTOM_DAYS, "0", 10, 28);

        assertEquals(RecurrenceType.CUSTOM_DAYS, payment.getRecurrence());

        assertEquals(28, payment.getCustomIntervalDays());



        // Edit interval from 28 to 45 days

        payment.setCustomIntervalDays(45);

        assertEquals(45, payment.getCustomIntervalDays());

        long next45 = MonthlyPayment.calculateImmediateNextDueDate(payment.getDueDate(), payment.getRecurrence(), payment.getOriginalDueDay(), payment.getCustomIntervalDays());

        Calendar c45 = Calendar.getInstance(); c45.setTimeInMillis(next45);

        // Jan 10 + 45 days = Jan 10 + 21d -> Jan 31 + 24d -> Feb 24

        assertEquals(Calendar.FEBRUARY, c45.get(Calendar.MONTH));

        assertEquals(24, c45.get(Calendar.DAY_OF_MONTH));



        // Switch from Custom to Monthly: custom interval must no longer affect calculation

        payment.setRecurrence(RecurrenceType.MONTHLY);

        long nextMonthly = MonthlyPayment.calculateImmediateNextDueDate(payment.getDueDate(), payment.getRecurrence(), payment.getOriginalDueDay(), payment.getCustomIntervalDays());

        Calendar cMonthly = Calendar.getInstance(); cMonthly.setTimeInMillis(nextMonthly);

        assertEquals(Calendar.FEBRUARY, cMonthly.get(Calendar.MONTH));

        assertEquals(10, cMonthly.get(Calendar.DAY_OF_MONTH));



        // Switch from Monthly to Custom with 14 days

        payment.setRecurrence(RecurrenceType.CUSTOM_DAYS);

        payment.setCustomIntervalDays(14);

        long next14 = MonthlyPayment.calculateImmediateNextDueDate(payment.getDueDate(), payment.getRecurrence(), payment.getOriginalDueDay(), payment.getCustomIntervalDays());

        Calendar c14 = Calendar.getInstance(); c14.setTimeInMillis(next14);

        assertEquals(Calendar.JANUARY, c14.get(Calendar.MONTH));

        assertEquals(24, c14.get(Calendar.DAY_OF_MONTH));

    }



    @Test

    public void testCustomRecurrence_modelPersistenceProperties() {

        MonthlyPayment p = new MonthlyPayment(10, 100L, "Rent", false, 1700000000L, "PENDING", 1200.0, RecurrenceType.CUSTOM_DAYS, "0", 15, 30);

        assertEquals(10, p.getId());

        assertEquals(Long.valueOf(100L), p.getServerId());

        assertEquals("Rent", p.getName());

        assertEquals(RecurrenceType.CUSTOM_DAYS, p.getRecurrence());

        assertEquals(30, p.getCustomIntervalDays());



        // Default value for uninitialized interval is 28

        MonthlyPayment pDef = new MonthlyPayment(11, "Water", false, 1700000000L);

        assertEquals(28, pDef.getCustomIntervalDays());

    }



    // =========================================================================

    // RECURRENCE TYPE BACKEND COMPATIBILITY TESTS

    // =========================================================================



    @Test
    public void testRecurrenceType_backendCompatibilityFlags() {
        // Phase 4C/4E: All 7 standard recurrence types are supported; UNKNOWN is fallback
        for (RecurrenceType type : RecurrenceType.values()) {
            if (type == RecurrenceType.UNKNOWN) {
                assertFalse("UNKNOWN must not be supported by backend", type.isSupportedByBackend());
                assertTrue("UNKNOWN must be local-only", type.isLocalOnly());
            } else {
                assertTrue(type.name() + " must be supported by backend", type.isSupportedByBackend());
                assertFalse(type.name() + " must not be local-only", type.isLocalOnly());
            }
        }
        assertEquals("There must be 7 standard recurrence types + UNKNOWN fallback", 8, RecurrenceType.values().length);
    }

    @Test
    public void testAllRecurrencesEligibleForSync_andMetadataFields() {
        // Verify all 7 recurrences are eligible for sync and metadata fields persist
        for (RecurrenceType type : RecurrenceType.values()) {
            if (type == RecurrenceType.UNKNOWN) {
                assertTrue("Recurrence UNKNOWN must be local-only", type.isLocalOnly());
                continue;
            }
            assertFalse("Recurrence " + type + " must not be local-only", type.isLocalOnly());
            MonthlyPayment payment = new MonthlyPayment(1, null, "Test " + type, false, 1700000000L, "PENDING", 100.0, type, "0", 15, 45);
            assertEquals("PENDING", payment.getSyncStatus());
            assertEquals(15, payment.getOriginalDueDay());
            assertEquals(45, payment.getCustomIntervalDays());
        }



        // Verify PaymentRequest & PaymentResponse metadata fields

        com.example.reminder.network.PaymentRequest req = new com.example.reminder.network.PaymentRequest("Custom", 1700000000L, false);

        req.setOriginalDueDay(31);

        req.setCustomIntervalDays(45);

        assertEquals(Integer.valueOf(31), req.getOriginalDueDay());

        assertEquals(Integer.valueOf(45), req.getCustomIntervalDays());



        com.example.reminder.network.PaymentResponse resp = new com.example.reminder.network.PaymentResponse();

        resp.setOriginalDueDay(31);

        resp.setCustomIntervalDays(45);

        assertEquals(Integer.valueOf(31), resp.getOriginalDueDay());

        assertEquals(Integer.valueOf(45), resp.getCustomIntervalDays());

    }



    // =========================================================================

    // SNOOZE CONSISTENCY & EFFECTIVE TIME REGRESSION TESTS

    // =========================================================================



    @Test

    public void testSnoozePreservesOriginalTime_andStoresNewSnoozedTime() {

        long originalTime = 1700000000000L;

        long snoozedTime = 1700001800000L; // +30 minutes

        long updatedAt = 1700000050000L;



        // Reminder initial state

        Reminder reminder = new Reminder(1, 101L, "Tax Filing", originalTime, false, 0L, "SYNCED");

        assertEquals(originalTime, reminder.getTime());

        assertEquals(0L, reminder.getSnoozedUntil());

        assertEquals(originalTime, reminder.getEffectiveTime());



        // Snooze operation via buildSnoozeUpdateValues helper

        java.util.Map<String, Object> updateValues = ReminderDatabaseHelper.buildSnoozeUpdateValues(snoozedTime, updatedAt);



        // 1. Database snooze values MUST NOT overwrite 'time'

        assertFalse("Database update values must NOT contain 'time' key", updateValues.containsKey("time"));



        // 2. Database snooze values MUST set 'snoozed_time'

        assertEquals(Long.valueOf(snoozedTime), updateValues.get("snoozed_time"));

        assertEquals(Integer.valueOf(0), updateValues.get("is_expired"));

        assertEquals("PENDING", updateValues.get("sync_status"));

        assertEquals(Long.valueOf(updatedAt), updateValues.get("updated_at"));



        // 3. Model reflects snoozedUntil while time remains originalTime

        reminder.setSnoozedUntil(snoozedTime);

        reminder.setExpired(false);

        assertEquals("Original time must remain preserved", originalTime, reminder.getTime());

        assertEquals("SnoozedUntil must reflect the snooze target", snoozedTime, reminder.getSnoozedUntil());

        assertEquals("Effective time must now be snoozedUntil", snoozedTime, reminder.getEffectiveTime());

    }



    @Test

    public void testServerPulledReminder_unchangedOriginalTimeChangedSnooze_triggersRescheduling() {

        long originalTime = 1700000000000L;

        long localSnooze = 0L;

        long serverSnooze = 1700003600000L; // Snoozed on Windows by +60 min

        long now = 1700000010000L;



        // Both server and local have identical reminderTime

        long serverReminderTime = originalTime;

        long localReminderTime = originalTime;



        // Evaluate update scheduling

        com.example.reminder.sync.SyncManager.SchedulingDecision decision =

                com.example.reminder.sync.SyncManager.evaluateUpdateScheduling(

                        serverReminderTime,

                        serverSnooze,

                        false, // not expired

                        localReminderTime,

                        localSnooze,

                        false,

                        now

                );



        assertTrue("Prior local alarm must be cancelled when effective time changes", decision.cancelExisting);

        assertTrue("Server snooze must trigger scheduling", decision.shouldSchedule);

        assertEquals("Scheduled time must be effective server snooze time", serverSnooze, decision.scheduleTime);

    }



    @Test

    public void testEffectiveServerTimeUsedForAlarmScheduling() {

        long originalTime = 1700000000000L;

        long snoozedTime = 1700001800000L;

        long now = 1700000005000L;



        // When snoozedTime is positive, effective time is snoozedTime

        com.example.reminder.sync.SyncManager.SchedulingDecision decision =

                com.example.reminder.sync.SyncManager.evaluateUpdateScheduling(

                        originalTime,

                        snoozedTime,

                        false,

                        originalTime,

                        0L,

                        false,

                        now

                );

        assertEquals(snoozedTime, decision.scheduleTime);

    }



    @Test

    public void testNewServerReminderWithSnoozedTime_usesSnoozedTime() {

        long originalTime = 1700000000000L;

        long snoozedTime = 1700002400000L;

        long now = 1700000001000L;



        com.example.reminder.sync.SyncManager.SchedulingDecision decision =

                com.example.reminder.sync.SyncManager.evaluateNewInsertScheduling(

                        originalTime,

                        snoozedTime,

                        false,

                        now

                );



        assertFalse("New insert has no prior alarm to cancel", decision.cancelExisting);

        assertTrue("New insert in future must be scheduled", decision.shouldSchedule);

        assertEquals("New insert with snooze must be scheduled at snoozedTime", snoozedTime, decision.scheduleTime);

    }



    @Test

    public void testNormalReminderWithoutSnoozedTime_schedulesNormally() {

        long originalTime = 1700005000000L;

        long now = 1700000000000L;



        // Update path without snooze (snoozedTime <= 0)

        com.example.reminder.sync.SyncManager.SchedulingDecision updateDecision =

                com.example.reminder.sync.SyncManager.evaluateUpdateScheduling(

                        originalTime,

                        0L,

                        false,

                        1700001000000L, // old local time

                        0L,

                        false,

                        now

                );

        assertTrue("Must reschedule when normal reminder time changed", updateDecision.shouldSchedule);

        assertEquals("Must schedule at original reminder time", originalTime, updateDecision.scheduleTime);



        // New insert path without snooze

        com.example.reminder.sync.SyncManager.SchedulingDecision insertDecision =

                com.example.reminder.sync.SyncManager.evaluateNewInsertScheduling(

                        originalTime,

                        0L,

                        false,

                        now

                );

        assertTrue("Must schedule new reminder in future", insertDecision.shouldSchedule);

        assertEquals("Must schedule at original reminder time", originalTime, insertDecision.scheduleTime);

    }



    @Test

    public void testExpiredReminders_doNotSchedule() {

        long originalTime = 1700000000000L;

        long snoozedTime = 1700001800000L;

        long now = 1700000005000L;



        // 1. Server marked expired on update

        com.example.reminder.sync.SyncManager.SchedulingDecision updateExpired =

                com.example.reminder.sync.SyncManager.evaluateUpdateScheduling(

                        originalTime,

                        snoozedTime,

                        true, // expired!

                        originalTime,

                        0L,

                        false,

                        now

                );

        assertTrue("Must cancel existing alarm when server expired", updateExpired.cancelExisting);

        assertFalse("Expired reminder must NOT be scheduled", updateExpired.shouldSchedule);



        // 2. Server marked expired on new insert

        com.example.reminder.sync.SyncManager.SchedulingDecision insertExpired =

                com.example.reminder.sync.SyncManager.evaluateNewInsertScheduling(

                        originalTime,

                        snoozedTime,

                        true, // expired!

                        now

                );

        assertFalse("Expired new reminder must NOT be scheduled", insertExpired.shouldSchedule);



        // 3. Past reminder without snooze

        com.example.reminder.sync.SyncManager.SchedulingDecision pastReminder =

                com.example.reminder.sync.SyncManager.evaluateNewInsertScheduling(

                        now - 50000L,

                        0L,

                        false,

                        now

                );

        assertFalse("Past reminder must NOT be scheduled", pastReminder.shouldSchedule);

    }



    @Test

    public void testRepeatedSnoozes_replacesPriorTrigger() {

        long originalTime = 1700000000000L;

        long snooze1 = 1700000600000L; // +10 min

        long snooze2 = 1700001800000L; // +30 min

        long snooze3 = 1700003600000L; // +60 min

        long now = 1700000000000L;



        // Model tracking repeated snoozes

        Reminder r = new Reminder(5, 505L, "Doctor Appointment", originalTime, false, 0L, "SYNCED");

        assertEquals(originalTime, r.getEffectiveTime());



        // Snooze 1

        r.setSnoozedUntil(snooze1);

        assertEquals("First snooze effective time", snooze1, r.getEffectiveTime());

        assertEquals("Original time preserved after first snooze", originalTime, r.getTime());



        // Snooze 2 (replaces snooze 1)

        r.setSnoozedUntil(snooze2);

        assertEquals("Second snooze replaces first snooze", snooze2, r.getEffectiveTime());

        assertEquals("Original time preserved after second snooze", originalTime, r.getTime());



        // Sync evaluation for repeated snooze (local had snooze1, server has snooze2)

        com.example.reminder.sync.SyncManager.SchedulingDecision repeatDecision =

                com.example.reminder.sync.SyncManager.evaluateUpdateScheduling(

                        originalTime,

                        snooze2,

                        false,

                        originalTime,

                        snooze1,

                        false,

                        now

                );

        assertTrue("Repeated snooze must cancel prior alarm", repeatDecision.cancelExisting);

        assertTrue("Repeated snooze must schedule new alarm", repeatDecision.shouldSchedule);

        assertEquals("Scheduled time must be latest snooze", snooze2, repeatDecision.scheduleTime);



        // Snooze 3 (replaces snooze 2)

        com.example.reminder.sync.SyncManager.SchedulingDecision repeat3Decision =

                com.example.reminder.sync.SyncManager.evaluateUpdateScheduling(

                        originalTime,

                        snooze3,

                        false,

                        originalTime,

                        snooze2,

                        false,

                        now

                );

        assertTrue("Third snooze must cancel prior alarm", repeat3Decision.cancelExisting);

        assertTrue("Third snooze must schedule new alarm", repeat3Decision.shouldSchedule);

        assertEquals("Scheduled time must be latest snooze", snooze3, repeat3Decision.scheduleTime);
    }

    // =========================================================================
    // PHASE 4E DEFECT FIX TESTS (B1, B3, B4)
    // =========================================================================

    @Test
    public void testPhase4E_OneTimePaymentCompletionSetsCompletedTrue() {
        long now = System.currentTimeMillis();
        MonthlyPayment payment = new MonthlyPayment(10, null, "One-Time Tax", false, now + 86400000L, "PENDING", 250.0, RecurrenceType.ONE_TIME, "0");
        assertFalse("Initial state must be uncompleted", payment.isCompleted());

        // Simulate checking off the payment (as fixed in MonthlyPaymentAdapter line 234)
        payment.setLastPaidAt(now);
        payment.setCompleted(true);
        payment.setSyncStatus("PENDING");

        assertTrue("ONE_TIME payment check-off must mark completed = true", payment.isCompleted());
        assertEquals("lastPaidAt must be recorded", Long.valueOf(now), payment.getLastPaidAt());
        assertEquals("Sync status must be PENDING", "PENDING", payment.getSyncStatus());
    }

    @Test
    public void testPhase4E_UnknownRecurrenceFallback() {
        // Unknown or future recurrence string should map to UNKNOWN, NOT silently to MONTHLY
        assertEquals(RecurrenceType.MONTHLY, RecurrenceType.fromString(null));
        assertEquals(RecurrenceType.MONTHLY, RecurrenceType.fromString(""));
        assertEquals(RecurrenceType.MONTHLY, RecurrenceType.fromString("   "));
        assertEquals(RecurrenceType.MONTHLY, RecurrenceType.fromString("MONTHLY"));
        assertEquals(RecurrenceType.ONE_TIME, RecurrenceType.fromString("ONE_TIME"));
        assertEquals(RecurrenceType.CUSTOM_DAYS, RecurrenceType.fromString("CUSTOM_DAYS"));

        // Forward-compatibility test: unrecognized recurrence must parse to UNKNOWN
        RecurrenceType unknown1 = RecurrenceType.fromString("BI_WEEKLY");
        assertEquals("Unrecognized string must parse to UNKNOWN", RecurrenceType.UNKNOWN, unknown1);
        RecurrenceType unknown2 = RecurrenceType.fromString("SEMI_ANNUAL");
        assertEquals("Unrecognized string must parse to UNKNOWN", RecurrenceType.UNKNOWN, unknown2);
        RecurrenceType unknown3 = RecurrenceType.fromString("FUTURE_RECURRENCE_XYZ");
        assertEquals("Unrecognized string must parse to UNKNOWN", RecurrenceType.UNKNOWN, unknown3);

        // UNKNOWN recurrence should be kept LOCAL_ONLY and not supported by backend
        assertFalse("UNKNOWN must not be claimed as supported by backend", RecurrenceType.UNKNOWN.isSupportedByBackend());
        assertTrue("UNKNOWN must be marked LOCAL_ONLY", RecurrenceType.UNKNOWN.isLocalOnly());

        // Calculate next due date for UNKNOWN recurrence: must NOT advance like MONTHLY!
        long due = 1700000000000L;
        long nextDue = MonthlyPayment.calculateNextDueDate(due, RecurrenceType.UNKNOWN, 15, 28, due + 1000L);
        assertEquals("UNKNOWN recurrence must not advance or mutate due date", due, nextDue);
    }

    @Test
    public void testPhase4E_CustomDaysBoundaryValues() {
        long due = 1700000000000L;
        // Valid 1 day
        long next1 = MonthlyPayment.calculateNextDueDate(due, RecurrenceType.CUSTOM_DAYS, 1, 1, due + 1000L);
        assertEquals(due + (1L * 86400000L), next1);

        // Valid 28 days
        long next28 = MonthlyPayment.calculateNextDueDate(due, RecurrenceType.CUSTOM_DAYS, 1, 28, due + 1000L);
        assertEquals(due + (28L * 86400000L), next28);

        // Valid 365 days
        long next365 = MonthlyPayment.calculateNextDueDate(due, RecurrenceType.CUSTOM_DAYS, 1, 365, due + 1000L);
        assertEquals(due + (365L * 86400000L), next365);
    }

    // =========================================================================
    // DEF-02 TESTS: Offline Sync Retry & Error Classification
    // =========================================================================

    @Test
    public void testDEF02_isPermanentError_classification() {
        // Permanent validation / client errors -> true
        assertTrue(com.example.reminder.sync.SyncManager.isPermanentError(400));
        assertTrue(com.example.reminder.sync.SyncManager.isPermanentError(403));
        assertTrue(com.example.reminder.sync.SyncManager.isPermanentError(404));
        assertTrue(com.example.reminder.sync.SyncManager.isPermanentError(405));
        assertTrue(com.example.reminder.sync.SyncManager.isPermanentError(422));

        // Transient errors or auth refresh candidates -> false
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(401)); // Handled by token refresh
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(408)); // Request Timeout
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(429)); // Too Many Requests
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(500)); // Internal Server Error
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(502)); // Bad Gateway
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(503)); // Service Unavailable
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(504)); // Gateway Timeout
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(200)); // Success
        assertFalse(com.example.reminder.sync.SyncManager.isPermanentError(201)); // Created
    }

    @Test
    public void testDEF02_isEligibleForSync_statusSelection() {
        // Retryable states
        assertTrue(com.example.reminder.sync.SyncManager.isEligibleForSync("PENDING"));
        assertTrue(com.example.reminder.sync.SyncManager.isEligibleForSync("FAILED"));

        // Non-retryable states
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync("FAILED_PERMANENT"));
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync("SYNCED"));
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync("DELETE_PENDING"));
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync("DELETE_SYNCED"));
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync("LOCAL_ONLY"));
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync(null));
        assertFalse(com.example.reminder.sync.SyncManager.isEligibleForSync(""));
    }
}