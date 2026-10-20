package com.stepvolt.app.ui;

import android.app.DatePickerDialog;
import android.content.Context;

import com.stepvolt.app.util.ProfileRules;

import java.util.Calendar;

/** Calendário para a data de nascimento (1900 até hoje). Devolve AAAA-MM-DD. */
public final class DatePickerHelper {

    private DatePickerHelper() {}

    public interface OnDate { void picked(String iso); }

    public static void show(Context ctx, String currentIso, final OnDate cb) {
        Calendar cal = Calendar.getInstance();
        int[] parsed = ProfileRules.parseDate(currentIso);
        if (parsed != null) cal.set(parsed[0], parsed[1] - 1, parsed[2]);
        else cal.set(cal.get(Calendar.YEAR) - 25, 0, 1);
        DatePickerDialog d = new DatePickerDialog(ctx, (picker, y, m, day) ->
                cb.picked(ProfileRules.formatDate(y, m + 1, day)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        Calendar min = Calendar.getInstance();
        min.set(1900, 0, 1);
        d.getDatePicker().setMinDate(min.getTimeInMillis());
        d.getDatePicker().setMaxDate(System.currentTimeMillis());
        d.show();
    }
}
