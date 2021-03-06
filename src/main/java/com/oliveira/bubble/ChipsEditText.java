package com.oliveira.bubble;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.util.ArrayList;

@SuppressWarnings("unused")
public class ChipsEditText extends MultilineEditText {

    private ArrayList<AutoCompletePopover.Entity> availableItems =
            new ArrayList<AutoCompletePopover.Entity>();

    private ArrayList<AutoCompletePopover.Entity> filteredItems =
            new ArrayList<AutoCompletePopover.Entity>();

    private AutoCompletePopover popover;
    private AutoCompleteManager manager;
    private BubbleStyle currentStyle;
    private String triggerChar = "#";

    private boolean autoShow;
    private int maxBubbleCount = -1;

    public CharSequence savedHint;
    protected EditAction lastEditAction;

    public ChipsEditText(Context context) {
        super(context);
        init();
    }

    public ChipsEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChipsEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    void init() {

        manager = new AutoCompleteManager();
        manager.setResolver(new AutoCompleteManager.Resolver() {

            @Override
            public ArrayList<AutoCompletePopover.Entity> getSuggestions(String query) throws Exception {
                if (resolver == null)
                    return null;
                return resolver.getSuggestions(query);
            }

            @Override
            public ArrayList<AutoCompletePopover.Entity> getDefaultSuggestions() {
                return resolver.getDefaultSuggestions();
            }

            @Override
            public void update(String query, ArrayList<AutoCompletePopover.Entity> results) {
                setAvailableItems(results);
            }
        });

        addTextChangedListener(hashWatcher);
        addTextChangedListener(autocompleteWatcher);
        setOnEditorActionListener(editorActionListener);

        setCursorVisible(false);
        float width = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                getContext().getResources().getDisplayMetrics());

        this.cursorDrawable = new CursorDrawable(this, getTextSize()*1.5f, width, getContext());
        this.savedHint = getHint();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(cursorRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(cursorRunnable);
    }

    Runnable cursorRunnable = new Runnable() {
        @Override
        public void run() {
            cursorBlink = !cursorBlink;
            postInvalidate();
            postDelayed(cursorRunnable, 500);
        }
    };

    public BubbleStyle getCurrentStyle() {
        return currentStyle;
    }

    public void setCurrentStyle(BubbleStyle currentStyle) {
        this.currentStyle = currentStyle;
    }

    boolean cursorBlink;
    CursorDrawable cursorDrawable;

    @Override
    public boolean onPreDraw() {

        CharSequence hint = getHint();
        boolean empty = TextUtils.isEmpty(getText());

        if (manualModeOn && empty) {
            if (!TextUtils.isEmpty(hint)) {
                setHint("");
            }
        } else if (!manualModeOn && empty && !TextUtils.isEmpty(savedHint)) {
            setHint(savedHint);
        }

        return super.onPreDraw();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isFocused()) {
            cursorDrawable.draw(canvas, cursorBlink);
        }
    }

    public void resetAutocompleList() {
        lastEditAction = null;
        manager.search("");
    }

    public String getTriggerChar() {
        return triggerChar;
    }

    public void setTriggerChar(String triggerChar) {
        this.triggerChar = triggerChar;
    }

    public void setAutocomplePopover(AutoCompletePopover popover) {
        this.popover = popover;
    }

    public void addBubble(String text, int start, Object data) {

        if (start > getText().length()) {
            start = getText().length();
        }

        getText().insert(start, text);
        makeChip(start, start+text.length(), true, data);
        onBubbleCountChanged();
    }

    boolean finalizing;

    public void makeChip(int start, int end, boolean finalize, Object data) {

        if (finalizing) {
            return;
        }

        int maxWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        String finalText = null;
        if (finalize) {

            finalizing = true;
            try {
                getText().insert(start, " ");
                getText().insert(end + 1, " ");
                end += 2;
                finalText = getText().subSequence(start + 1, end - 1).toString();
            } catch (java.lang.IndexOutOfBoundsException e) {
                finalizing = false;
                return;
            }
        }

        int textSize = (int)(getTextSize() - TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1,
                getContext().getResources().getDisplayMetrics()));

        BubbleStyle bubbleStyle = DefaultBubbles.get(DefaultBubbles.GREEN, getContext(), textSize);
        if (currentStyle != null) {
            bubbleStyle = currentStyle;
        }

        Utils.bubblify(getText(),
                finalText,
                start,
                end,
                maxWidth,
                bubbleStyle,
                this,
                data);

        finalizing = false;
    }

    boolean manualModeOn;
    int manualStart;

    public void setMaxBubbleCount(int maxBubbleCount) {
        this.maxBubbleCount = maxBubbleCount;
    }

    public boolean canAddMoreBubbles() {
        return maxBubbleCount == -1 || getBubbleCount() < maxBubbleCount;
    }

    public int getBubbleCount() {
        try {
            return getText().getSpans(0, getText().length(), BubbleSpan.class).length;
        } catch (Exception e) {
            return 0;
        }
    }

    public BubbleSpanImpl[] getBubbleList() {
        return getText().getSpans(0, getText().length(), BubbleSpanImpl.class);
    }

    public void startManualMode() {

        resetAutocompleList();
        if (!canAddMoreBubbles()) {
            return;
        }

        int i = getSelectionStart() - 1;
        if (i >= 0 && (!Character.isWhitespace(getText().charAt(i)) || hasBubbleAt(i))) {
            getText().insert(i+1, " ");
        }

        lastEditAction = null;
        manualModeOn = true;
        manualStart = getSelectionStart();
    }

    public boolean hasBubbleAt(int position) {
        return getText().getSpans(position, position+1, BubbleSpanImpl.class).length > 0;
    }

    public void endManualMode() {

        boolean madeChip = false;
        if (manualStart < getSelectionEnd() && manualModeOn) {
            makeChip(manualStart, getSelectionEnd(), true, null);
            madeChip = true;
            onBubbleCountChanged();
        }

        manualModeOn = false;
        popover.hide();

        if (madeChip && getSelectionEnd() == getText().length()) {
            getText().append(" ");
            setSelection(getText().length());
        }
    }

    public void cancelManualMode() {
        if (manualStart < getSelectionEnd() && manualModeOn) {
            getText().delete(manualStart, getSelectionEnd());
        }
        manualModeOn = false;
        popover.hide();
    }

    TextWatcher autocompleteWatcher = new TextWatcher() {
        ReplacementSpan manipulatedSpan;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            manipulatedSpan = null;
            if (after < count && !manualModeOn) {
                ReplacementSpan[] spans = ((Spannable)s).getSpans(start, start+count, ReplacementSpan.class);
                if (spans.length == 1) {
                    manipulatedSpan = spans[0];
                } else {
                    manipulatedSpan = null;
                }
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

            if (!shouldShow()) {
                return;
            }

            String textForAutocomplete;
            try {

                if (manualModeOn && manualStart < start) {
                    count += start - manualStart;
                    start = manualStart;
                }

                textForAutocomplete = s.toString().substring(start, start+count);
                if (resolver != null)
                    manager.search(textForAutocomplete);
                if (!TextUtils.isEmpty(textForAutocomplete)) {
                    showAutocomplete(new EditAction(textForAutocomplete, start, before, count));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void afterTextChanged(Editable s) {

            if (manualModeOn) {

                int end = getSelectionStart();
                if (end < manualStart) {
                    manualModeOn = false;
                } else {
                    makeChip(manualStart, end, false, null);
                }

            } else if (manipulatedSpan != null) {

                int start = s.getSpanStart(manipulatedSpan);
                int end = s.getSpanEnd(manipulatedSpan);
                if (start > -1 && end > -1) {
                    s.delete(start, end);
                }

                onBubbleCountChanged();
                manipulatedSpan = null;
                manualModeOn = false;
            }

            if (manualModeOn) {
                popover.reposition();
            }
            else {
                popover.hide();
            }
        }
    };

    protected void setAvailableItems(ArrayList<AutoCompletePopover.Entity> items) {
        popover.scrollToTop();
        availableItems = items;
        filter();
    }

    private void removeByText(String text) {

        AutoCompletePopover.Entity toRemove = null;
        for (AutoCompletePopover.Entity item : availableItems) {
            if (text.equals(item.label)) {
                toRemove = item;
                break;
            }
        }

        if (toRemove != null) {
            availableItems.remove(toRemove);
        }
    }

    private void filter() {

        ArrayList<AutoCompletePopover.Entity> availableItems =
                new ArrayList<AutoCompletePopover.Entity>();

        if (this.availableItems != null) {
            for (AutoCompletePopover.Entity item : this.availableItems) {
                item.label = item.label.trim();
                availableItems.add(item);
            }
        }

        if (availableItems.size() > 0) {
            BubbleSpan[] spans = getText().getSpans(0, getText().length(), BubbleSpan.class);
            for (BubbleSpan span : spans) {

                int start = getText().getSpanStart(span);
                int end = getText().getSpanEnd(span);

                if (start == -1 || end == -1 || end <= start || (manualStart == start && manualModeOn)) {
                    continue;
                }

                String text = getText().subSequence(start, end).toString().trim();
                removeByText(text);
            }
        }

        filteredItems.clear();
        if (lastEditAction != null) {
            String text = lastEditAction.text.toLowerCase();
            if (!TextUtils.isEmpty(text))
                for (AutoCompletePopover.Entity item : availableItems) {
                    if ((text.length() > 1 && item.label.toLowerCase().startsWith(text))
                            || (manualModeOn && item.label.toLowerCase().contains(text) && text.length() > 3)) {
                        filteredItems.add(item);
                    }
                }
        }

        if (filteredItems.size() > 0) {
            popover.setItems(filteredItems);
            if (shouldShow()) {
                popover.show();
            }
        } else {
            if (!manualModeOn) {
                popover.hide();
            }
            popover.setItems(availableItems);
        }
    }

    private boolean shouldShow() {
        return autoShow || manualModeOn;
    }

    public void showAutocomplete(EditAction editAction) {
        lastEditAction = editAction;
        filter();
    }

    TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {

            if (keyEvent == null) {
                if (actionId == EditorInfo.IME_ACTION_UNSPECIFIED)
                    actionId = EditorInfo.IME_ACTION_DONE;
                if (actionId == EditorInfo.IME_ACTION_DONE && manualModeOn) {
                    cancelManualMode();
                    onActionDone();
                    return true;
                } else if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard();
                    onActionDone();
                    return true;
                }
            } else if (actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                return true;
            }

            return false;
        }
    };

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
        popover.hide();
    }

    public void showKeyboard() {
        InputMethodManager inputMgr = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMgr.showSoftInput(this, InputMethodManager.SHOW_FORCED);
    }

    public Point getInnerCursorPosition() {
        int pos = getSelectionStart();
        Layout layout = getLayout();
        if (layout == null) {
            return new Point(0, 0);
        }
        int line = layout.getLineForOffset(pos);
        int baseline = layout.getLineBaseline(line);
        int ascent = layout.getLineAscent(line);
        float x = layout.getPrimaryHorizontal(pos);
        float y = baseline + ascent;
        return new Point((int)x, (int)y);
    }

    public Point getCursorPosition() {
        Point p = getInnerCursorPosition();
        p.offset(getPaddingLeft(), getPaddingTop());
        return p;
    }

    public static class EditAction {
        String text;
        int start;
        int before;
        int count;
        int end() {
            return start + count;
        }

        public EditAction(String text, int start, int before, int count) {
            this.text = text;
            this.start = start;
            this.before = before;
            this.count = count;
        }
    }

    public void setAutocompleteResolver(AutocompleteResolver resolver) {
        this.resolver = resolver;
    }

    private AutocompleteResolver resolver;

    public interface AutocompleteResolver {
        public ArrayList<AutoCompletePopover.Entity> getSuggestions(String query) throws Exception;
        public ArrayList<AutoCompletePopover.Entity> getDefaultSuggestions();
    }

    boolean muteHashWatcher;
    protected void muteHashWatcher(boolean value) {
        muteHashWatcher = value;
    }

    private TextWatcher hashWatcher = new TextWatcher() {
        String before;
        String after;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (muteHashWatcher)
                return;
            before = s.toString();
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (muteHashWatcher)
                return;
            after = s.toString();
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (muteHashWatcher)
                return;
            if (after.length() > before.length() && after.lastIndexOf(triggerChar) > before.lastIndexOf(triggerChar)) {
                int lastIndex = after.lastIndexOf(triggerChar);
                if (manualModeOn || canAddMoreBubbles())
                    s.delete(lastIndex, lastIndex + 1);
                if (!manualModeOn) {
                    startManualMode();
                    popover.show();
                } else if (manualModeOn && manualStart < lastIndex) {
                    endManualMode();
                    if (canAddMoreBubbles()) {
                        startManualMode();
                    }
                }
            }
        }
    };

    private int previousWidth = 0;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (previousWidth != widthSize) {

            previousWidth = widthSize;
            int maxBubbleWidth = widthSize - getPaddingLeft() - getPaddingTop();

            Editable e = getText();
            BubbleSpan[] spans = e.getSpans(0, getText().length(), BubbleSpan.class);

            for (BubbleSpan span : spans) {
                span.resetWidth(maxBubbleWidth);
                int start = getText().getSpanStart(span);
                int end = getText().getSpanEnd(span);
                e.removeSpan(span);
                e.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private ArrayList<Listener> listeners = new ArrayList<Listener>();

    protected void onBubbleCountChanged() {
        for (Listener listener : listeners) {
            listener.onBubbleCountChanged();
        }
    }

    protected void onActionDone() {
        for (Listener listener : listeners) {
            listener.onActionDone();
        }
    }

    protected void onBubbleSelected(int position) {
        for (Listener listener : listeners) {
            listener.onBubbleSelected(position);
        }
    }

    protected void onXPressed() {
        for (Listener listener : listeners) {
            listener.onXPressed();
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        public void onBubbleCountChanged();
        public void onActionDone();
        public void onBubbleSelected(int position);
        public void onXPressed();
    }

    public CursorDrawable getCursorDrawable() {
        return this.cursorDrawable;
    }
}