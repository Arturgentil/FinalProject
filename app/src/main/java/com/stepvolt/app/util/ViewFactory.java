package com.stepvolt.app.util;

import android.content.Context;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.R;

public class ViewFactory {

    public static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    /** Adds a label + pill-shaped EditText row to `container` and returns the EditText. */
    public static EditText addLabeledInput(Context ctx, LinearLayout container, String label,
                                            String hint, int inputType, String prefillValue) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        tv.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 16);
        container.addView(tv, lp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_pill_input);
        int padH = dp(ctx, 16);
        row.setPadding(padH, 0, padH, 0);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(ctx, 6);
        container.addView(row, rowLp);

        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
        et.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        et.setBackground(null);
        et.setInputType(inputType);
        et.setTextSize(15);
        if (prefillValue != null) et.setText(prefillValue);
        int padV = dp(ctx, 14);
        et.setPadding(0, padV, 0, padV);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(et, etLp);
        return et;
    }
}
