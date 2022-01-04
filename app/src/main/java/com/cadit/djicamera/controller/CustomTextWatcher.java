package com.cadit.djicamera.controller;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

public abstract class CustomTextWatcher implements TextWatcher {
    private final TextView textView;

    public CustomTextWatcher (TextView textView) {
        this.textView = textView;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        String text = textView.getText().toString();
        validate(textView, text);
    }

    public abstract void validate(TextView textView, String text);
}
