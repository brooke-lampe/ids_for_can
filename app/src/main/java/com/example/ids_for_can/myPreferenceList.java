package com.example.ids_for_can;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class myPreferenceList extends ListPreference implements OnClickListener {

    private int mClickedDialogEntryIndexPrev;
    private int mClickedDialogEntryIndex;

    public myPreferenceList(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public myPreferenceList(Context context) {
        this(context, null);
    }

    private int getValueIndex() {
        return findIndexOfValue(this.getValue() + "");
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);

        mClickedDialogEntryIndex = getValueIndex();
        builder.setSingleChoiceItems(this.getEntries(), mClickedDialogEntryIndex, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
            }
        });

        if (this.getEntry() != null && this.getEntry().length() > 0 && this.getEntries() != null && this.getEntries().length > 0) {
            System.out.println(this.getEntry() + " " + this.getEntries()[0]);
        }
        builder.setPositiveButton("OK", this);
    }

    public void onClick (DialogInterface dialog, int which) {
        if (this.getEntryValues() != null && this.getEntryValues().length > 0) {
            if (which == -2 && this.getEntryValues().length > mClickedDialogEntryIndexPrev) {
                this.setValue(this.getEntryValues()[mClickedDialogEntryIndexPrev] + "");
            } else {
                mClickedDialogEntryIndexPrev = mClickedDialogEntryIndex;
                String value = this.getEntryValues()[mClickedDialogEntryIndex] + "";
                this.setValue(value);
                callChangeListener(value);
            }
        }
    }
}