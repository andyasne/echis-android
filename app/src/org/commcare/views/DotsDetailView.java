/**
 * 
 */
package org.commcare.views;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.commcare.dalvik.eCHIS.R;
import org.commcare.utils.DotsData.DotsBox;
import org.commcare.utils.DotsData.DotsDay;
import org.commcare.utils.DotsData.MedStatus;
import org.commcare.utils.DotsData.ReportType;
import org.commcare.utils.DotsEditListener;

/**
 * @author ctsims
 *
 */
public class DotsDetailView {
    
    private static final String DOSE_MORNING = "Morning";
    private static final String DOSE_NOON = "Noon";
    private static final String DOSE_EVENING = "Evening";
    private static final String DOSE_BEDTIME = "Bedtime";
    private static final String DOSE_UNKNOWN = "Dose";
    
    
    static final String[] labelMap = new String [] {DOSE_MORNING, DOSE_NOON, DOSE_EVENING, DOSE_BEDTIME};
    
    //For unnamed labels, these are the defaults.
    //TODO: 90% sure that only the last one here is relevant.
    public final static String[][] labels = new String[][] {
            new String[] {DOSE_UNKNOWN},
            new String[] {DOSE_MORNING, DOSE_EVENING},
            new String[] {DOSE_MORNING, DOSE_NOON, DOSE_EVENING},
            new String[] {DOSE_MORNING, DOSE_NOON, DOSE_EVENING, DOSE_BEDTIME}
    };
    
    public final static String[] regimens = new String[] { "Non-ART", "ART"};  
    
    View[] groups;
    EditText[] missedName;
    DotsDay day;
    int index;
    int dose;
    
    public DotsDetailView() {
        
    }

    public View LoadDotsDetailView(Context context, DotsDay day, int index, int dose, final DotsEditListener listener) {
        this.day = day;
        this.index = index;
        this.dose = dose;
        
        View view = View.inflate(context, R.layout.dots_detail, null);
        
        LinearLayout container = view.findViewById(R.id.dots_content_frame);
        
        container.removeAllViews();
        
        //groups = new RadioGroup[day.boxes().length];
        groups = new View[day.boxes().length];
        missedName = new EditText[day.boxes().length];
        
        int[] regimenIndices = day.getRegIndexes(dose);
        
        for(int i = 0; i < day.boxes().length; ++i) {
            int subIndex = regimenIndices[i];
            if(subIndex == -1) {
                continue;
            }
            DotsBox box = day.boxes()[i][subIndex];
            final View details = View.inflate(context, R.layout.compact_dot_entry, null);
            groups[i] = details;
            TextView timeView = details.findViewById(R.id.text_time);
            
            int doseName = box.getDoseLabel();
            String label = "";
            if(doseName == -1) {
                label = labels[day.getMaxReg() -1 ][dose];
            } else {
                if(doseName >= 0 && doseName < labelMap.length) {
                    label = labelMap[doseName];
                } else {
                    label = DOSE_UNKNOWN;
                }
            }
            timeView.setText(regimens[i] + ": " + label);
            
            details.setPadding(0, 0, 0,0);
            
            final View missingDetails = details.findViewById(R.id.missed_details);
            missedName[i] = details.findViewById(R.id.text_missed);
            
            //groups[i] = (RadioGroup)details.findViewById(R.id.dose_group);

            int type = R.id.tbt_pillbox; 
            
            switch(box.reportType()) {
            case self:
                type = R.id.tbt_self;
                break;
            case pillbox:
                type = R.id.tbt_pillbox;
                break;
            case direct:
                type = R.id.tbt_direct;
                break;
            }
            
            ToggleButton selectedReportType = details.findViewById(type);
            selectedReportType.setChecked(true);
                        
            int checked = -1;
            
            switch(box.status()) {
                case empty:
                    checked = R.id.radio_all;
                    break;
                case partial:
                    checked = R.id.radio_some;
                    missedName[i].setText(box.missedMeds());
                    missingDetails.setVisibility(View.VISIBLE);
                    break;
                case full:
                    checked = R.id.radio_none;
                    break;
                case unchecked:
                    checked = R.id.radio_unchecked;
                    break;
            }
            
            ToggleButton checkedToggle = details.findViewById(checked);
            checkedToggle.setChecked(true);
            
            //set up listeners
            final int[] ids = new int[] {R.id.radio_all, R.id.radio_some, R.id.radio_unchecked, R.id.radio_none};
            for(int id : ids) {
                ToggleButton toggle = details.findViewById(id);
                toggle.setOnClickListener(v -> {
                    for(int id1 : ids) {
                        if(v.getId() == id1) {
                            ((ToggleButton)v).setChecked(true);
                        } else {
                            ToggleButton toggle1 = details.findViewById(id1);
                            toggle1.setChecked(false);
                        }

                        if(v.getId() == R.id.radio_some) {
                            missingDetails.setVisibility(View.VISIBLE);
                        } else {
                            missingDetails.setVisibility(View.GONE);
                        }
                    }
                });
            }
            
            //set up listeners
            final int[] typeids = new int[] {R.id.tbt_direct, R.id.tbt_pillbox, R.id.tbt_self};
            for(int id : typeids) {
                ToggleButton toggle = details.findViewById(id);
                toggle.setOnClickListener(v -> {
                    for(int id12 : typeids) {
                        if(v.getId() == id12) {
                            ((ToggleButton)v).setChecked(true);
                        } else {
                            ToggleButton toggle12 = details.findViewById(id12);
                            toggle12.setChecked(false);
                        }
                    }
                });
            }
            
            if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.FILL_PARENT);
                params.leftMargin = 10;
                params.rightMargin = 10;
                params.weight = 1f / day.boxes().length;
                container.addView(details, params);
                container.setOrientation(LinearLayout.HORIZONTAL);
            } else {
                container.addView(details, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }
        
        Button ok = view.findViewById(R.id.btn_dots_detail_ok);
        Button cancel = view.findViewById(R.id.btn_dots_detail_cancel);
        
        ok.setOnClickListener(v -> listener.dayEdited(DotsDetailView.this.index, DotsDetailView.this.getDay()));
        
        cancel.setOnClickListener(v -> listener.cancelDoseEdit());
        
        return view;
    }
    
    public DotsDay getDay() {
        
        int[] regIndices = day.getRegIndexes(dose);
        //TODO: This is basically a shallow copy
        DotsBox[] newBoxes = new DotsBox[regIndices.length];
        
        
        //Now go fill in the new data
        for(int i =0 ; i < regIndices.length ; ++i) {
            if(regIndices[i] == -1) {
                newBoxes[i] = null;
                continue;
            }
            
            int checkedButton = -1;
            //Retrieve the selected value
            int[] ids = new int[] {R.id.radio_all, R.id.radio_some, R.id.radio_unchecked, R.id.radio_none};
            for(int id : ids) {
                ToggleButton button = groups[i].findViewById(id);
                if(button.isChecked()) {
                    checkedButton = id;
                }
            }
            
            int reportType = -1;
            //Retrieve the selected value
            int[] reportids = new int[] {R.id.tbt_direct, R.id.tbt_pillbox, R.id.tbt_self};
            for(int id : reportids) {
                ToggleButton button = groups[i].findViewById(id);
                if(button.isChecked()) {
                    reportType = id;
                }
            }

            
            MedStatus status = MedStatus.unchecked;
            String meds = null;
            
            switch(checkedButton) {
                case R.id.radio_all:
                    status= MedStatus.empty;
                    break;
                case R.id.radio_some:
                    status = MedStatus.partial;
                    meds = missedName[i].getText().toString();
                    break;
                case R.id.radio_none:
                    status = MedStatus.full;
                    break;
                case R.id.radio_unchecked:
                    status = MedStatus.unchecked;
                    break;
            }
            
            ReportType type = ReportType.pillbox;
            
            switch(reportType) {
                case R.id.tbt_direct:
                    type = ReportType.direct;
                    break;
                case R.id.tbt_pillbox:
                    type = ReportType.pillbox;
                    break;
                case R.id.tbt_self:
                    type = ReportType.self;
                    break;
            }
            
            newBoxes[i] = new DotsBox(status,type, meds, day.boxes()[i][regIndices[i]].getDoseLabel());
        }
        
        return day.updateDose(dose, newBoxes);
    }
}
