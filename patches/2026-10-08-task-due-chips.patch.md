# Patch: Due-date chips in Add task details

Apply inside `MainActivity.showTaskEditor` after `form.addView(dateActions, ...)`.

Replace:

```java
        form.addView(dateActions, bottomMargin(dp(14)));

        TextView priorityLabel = text("Priority", 13, palette.muted, Typeface.BOLD);
```

With:

```java
        form.addView(dateActions, bottomMargin(dp(8)));

        LinearLayout dueChips = new LinearLayout(this);
        dueChips.setOrientation(LinearLayout.HORIZONTAL);
        dueChips.setGravity(Gravity.CENTER_VERTICAL);
        LocalDate chipToday = LocalDate.now();
        Button chipTodayBtn = compactButton("Today", false);
        chipTodayBtn.setContentDescription("Set due date to today");
        chipTodayBtn.setOnClickListener(view -> {
            selectedDate[0] = TaskDuePresets.today(chipToday);
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        });
        Button chipTomorrowBtn = compactButton("Tomorrow", false);
        chipTomorrowBtn.setContentDescription("Set due date to tomorrow");
        chipTomorrowBtn.setOnClickListener(view -> {
            selectedDate[0] = TaskDuePresets.tomorrow(chipToday);
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        });
        Button chipWeekBtn = compactButton("+7 days", false);
        chipWeekBtn.setContentDescription("Set due date to one week from today");
        chipWeekBtn.setOnClickListener(view -> {
            selectedDate[0] = TaskDuePresets.nextWeek(chipToday);
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        });
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        chipLp.setMarginEnd(dp(6));
        dueChips.addView(chipTodayBtn, chipLp);
        LinearLayout.LayoutParams chipLp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        chipLp2.setMarginEnd(dp(6));
        dueChips.addView(chipTomorrowBtn, chipLp2);
        dueChips.addView(chipWeekBtn, new LinearLayout.LayoutParams(0, dp(44), 1f));
        form.addView(dueChips, bottomMargin(dp(14)));

        TextView priorityLabel = text("Priority", 13, palette.muted, Typeface.BOLD);
```

Also update home subtitle and quick-add feedback to mention Add details + chips (see commit message).

`TaskDuePresets.java` is already on `main`.
