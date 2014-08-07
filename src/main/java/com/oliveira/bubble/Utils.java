package com.oliveira.bubble;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

import java.util.HashMap;
import java.util.Map;

public class Utils {
   public static String flatten(EditText editor, HashMap<Class<?>, FlatteningFactory> factories) {
      try {
         return flatten(editor.getText(), factories);
      } catch (IndexOutOfBoundsException ioobe) {
         // known platform issue https://code.google.com/p/android/issues/detail?id=5164
         // workaround is to set seletion to the end
      }

      try {
         editor.setSelection(editor.getText().length(), editor.getText().length());
         return flatten(editor.getText(), factories);
      } catch (IndexOutOfBoundsException ioobe) {
         // oh well, we tried
      }

      try {
         return editor.getText().toString();
      } catch (Throwable t) {
         // we tried hard
      }

      return ""; // sorry
   }

   private static String flatten(CharSequence charSequence, HashMap<Class<?>, FlatteningFactory> factories) {
      Editable out = new SpannableStringBuilder(charSequence);
      for (Map.Entry<Class<?>, FlatteningFactory> e : factories.entrySet()) {
         Object spans[] = out.getSpans(0, out.length(), e.getKey());
         for (Object span : spans) {
            int start = out.getSpanStart(span);
            int end = out.getSpanEnd(span);
            String in = out.subSequence(start, end).toString();
            out.replace(start, end, e.getValue().out(in));
         }
      }
      return out.toString();
   }

   public interface FlatteningFactory {
      public String out(String in);
   }

   public static void bubblify(Editable editable, String text, int start, int end,
                               int maxWidth, BubbleStyle bubbleStyle, ChipsEditText et, Object data) {
      if (text == null) {
         text = editable.toString();
         if (start < 0)
            start = 0;
         if (end > text.length())
            end = text.length();
         text = text.substring(start, end);
      }

      // create bitmap drawable for ReplacementSpan
      TextPaint tp = new TextPaint();
      AwesomeBubble bubble = new AwesomeBubble(text, maxWidth, bubbleStyle, tp);

      // create and set ReplacementSpan
      ReplacementSpan[] spansToClear = editable.getSpans(start, end, ReplacementSpan.class);
      for (ReplacementSpan span : spansToClear)
         editable.removeSpan(span);
      BubbleSpan span = et == null ? new BubbleSpanImpl(bubble) : new BubbleSpanImpl(bubble, et);
      span.setData(data);
      editable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
   }

   public static void tapify(Editable editable, int start, int end, int activeColor, int inactiveColor, Object data) {
      BubbleSpan span = new TappableSpan(activeColor, inactiveColor);
      span.setData(data);
      editable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      span.setPressed(false, editable);
   }

   public final static String TEXT_ONLY = "text_only";
   public final static String TAGS_ONLY = "tags_only";
   public final static String MIXED = "mixed";
   public final static String TEXT_FIRST = "text_first";
   public final static String TAGS_FIRST = "tags_first";
   public final static String NONE = "none";

   /**
    * Calculates what kind of text setup this is
    * @param edit
    * @return TEXT_ONLY, TAGS_ONLY, MIXED, TEXT_FIRST, TAGS_FIRST, NONE
    */
   public static String tag_setup(ChipsEditText edit) {
      if (edit.length() == 0)
         return NONE;
      if (edit.getBubbleCount() == 0)
         return TEXT_ONLY;
      String assumption = null;
      Editable e = edit.getText();
      if (null == e)
         return NONE;
      boolean firstiesEnded = false;
      for (int i = 1; i <= e.length(); i++) {
         char c[] = new char[1];
         e.getChars(i-1, i, c, 0);
         if (Character.isWhitespace(c[0]))
            continue;
         boolean isSpan = e.getSpans(i-1, i, BubbleSpan.class).length > 0;
         if (assumption == null) {
            assumption = isSpan ? TAGS_FIRST : TEXT_FIRST;
         } else if (assumption.equals(TAGS_FIRST)) {
            if (!isSpan) {
               if (!firstiesEnded) {
                  firstiesEnded = true;
               }
            } else if (firstiesEnded) {
               return MIXED;
            }
         } else if (assumption.equals(TEXT_FIRST)) {
            if (isSpan) {
               if (!firstiesEnded) {
                  firstiesEnded = true;
               }
            } else if (firstiesEnded) {
               return MIXED;
            }
         }
      }
      if (null == assumption) {
         return NONE;
      }
      if (!firstiesEnded && assumption.equals(TAGS_FIRST)) {
         return  TAGS_ONLY;
      }
      return assumption;
   }
}
