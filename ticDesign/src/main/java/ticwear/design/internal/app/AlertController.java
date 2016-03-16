/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ticwear.design.internal.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Space;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import ticwear.design.R;
import ticwear.design.app.AlertDialog;
import ticwear.design.widget.FloatingActionButton;
import ticwear.design.widget.SubscribedScrollView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class AlertController {

    private final Context mContext;
    private final DialogInterface mDialogInterface;
    private final Window mWindow;

    private CharSequence mTitle;
    private CharSequence mMessage;
    private ListView mListView;
    private View mView;

    private int mViewLayoutResId;

    private int mViewSpacingLeft;
    private int mViewSpacingTop;
    private int mViewSpacingRight;
    private int mViewSpacingBottom;
    private boolean mViewSpacingSpecified = false;

    private final ButtonBundle mButtonBundlePositive;
    private final ButtonBundle mButtonBundleNegative;
    private final ButtonBundle mButtonBundleNeutral;

    private SubscribedScrollView mScrollView;

    private int mIconId = 0;
    private Drawable mIcon;

    private ImageView mIconView;
    private TextView mTitleView;
    private TextView mMessageView;
    private View mCustomTitleView;

    private boolean mForceInverseBackground;

    private ListAdapter mAdapter;

    private int mCheckedItem = -1;

    private int mAlertDialogLayout;
    private int mButtonPanelSideLayout;
    private int mListLayout;
    private int mMultiChoiceItemLayout;
    private int mSingleChoiceItemLayout;
    private int mListItemLayout;

    private int mButtonPanelLayoutHint = AlertDialog.LAYOUT_HINT_NONE;

    private Handler mHandler;

    private final View.OnClickListener mButtonHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Message m = mButtonBundlePositive.messageForButton(v);
            if (m == null) {
                m = mButtonBundleNegative.messageForButton(v);
            }
            if (m == null) {
                m = mButtonBundleNeutral.messageForButton(v);
            }

            if (m != null) {
                m.sendToTarget();
            }

            // Post a message so we dismiss after the above handlers are executed
            mHandler.obtainMessage(ButtonHandler.MSG_DISMISS_DIALOG, mDialogInterface)
                    .sendToTarget();
        }
    };

    private static final class ButtonHandler extends Handler {
        // Button clicks have Message.what as the BUTTON{1,2,3} constant
        private static final int MSG_DISMISS_DIALOG = 1;

        private WeakReference<DialogInterface> mDialog;

        public ButtonHandler(DialogInterface dialog) {
            mDialog = new WeakReference<DialogInterface>(dialog);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case DialogInterface.BUTTON_POSITIVE:
                case DialogInterface.BUTTON_NEGATIVE:
                case DialogInterface.BUTTON_NEUTRAL:
                    ((DialogInterface.OnClickListener) msg.obj).onClick(mDialog.get(), msg.what);
                    break;

                case MSG_DISMISS_DIALOG:
                    ((DialogInterface) msg.obj).dismiss();
            }
        }
    }

    private static boolean shouldCenterSingleButton(Context context) {
        return true;
    }

    public AlertController(Context context, DialogInterface di, Window window) {
        mContext = context;
        mDialogInterface = di;
        mWindow = window;
        mHandler = new ButtonHandler(di);

        TypedArray a = context.obtainStyledAttributes(null,
                R.styleable.AlertDialog,
                android.R.attr.alertDialogStyle, 0);

        mAlertDialogLayout = R.layout.alert_dialog_ticwear;
        mButtonPanelSideLayout = a.getResourceId(
                R.styleable.AlertDialog_android_buttonPanelSideLayout, 0);

        mListLayout = a.getResourceId(
                R.styleable.AlertDialog_android_listLayout,
                R.layout.select_dialog_ticwear);
        mMultiChoiceItemLayout = a.getResourceId(
                R.styleable.AlertDialog_android_multiChoiceItemLayout,
                android.R.layout.select_dialog_multichoice);
        mSingleChoiceItemLayout = a.getResourceId(
                R.styleable.AlertDialog_android_singleChoiceItemLayout,
                android.R.layout.select_dialog_singlechoice);
        mListItemLayout = a.getResourceId(
                R.styleable.AlertDialog_android_listItemLayout,
                android.R.layout.select_dialog_item);

        a.recycle();

        mButtonBundlePositive = new ButtonBundle();
        mButtonBundleNegative = new ButtonBundle();
        mButtonBundleNeutral = new ButtonBundle();
    }

    static boolean canTextInput(View v) {
        if (v.onCheckIsTextEditor()) {
            return true;
        }

        if (!(v instanceof ViewGroup)) {
            return false;
        }

        ViewGroup vg = (ViewGroup)v;
        int i = vg.getChildCount();
        while (i > 0) {
            i--;
            v = vg.getChildAt(i);
            if (canTextInput(v)) {
                return true;
            }
        }

        return false;
    }

    public void installContent() {
        /* We use a custom title so never request a window title */
        mWindow.requestFeature(Window.FEATURE_NO_TITLE);
        int contentView = selectContentView();
        mWindow.setContentView(contentView);
        setupView();
        setupDecor();
    }

    private int selectContentView() {
        if (mButtonPanelSideLayout == 0) {
            return mAlertDialogLayout;
        }
        if (mButtonPanelLayoutHint == AlertDialog.LAYOUT_HINT_SIDE) {
            return mButtonPanelSideLayout;
        }
        // TODO: use layout hint side for long messages/lists
        return mAlertDialogLayout;
    }

    public void setTitle(CharSequence title) {
        mTitle = title;
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
    }

    /**
     * @see AlertDialog.Builder#setCustomTitle(View)
     */
    public void setCustomTitle(View customTitleView) {
        mCustomTitleView = customTitleView;
    }

    public void setMessage(CharSequence message) {
        mMessage = message;
        if (mMessageView != null) {
            mMessageView.setText(message);
        }
    }

    /**
     * Set the view resource to display in the dialog.
     */
    public void setView(int layoutResId) {
        mView = null;
        mViewLayoutResId = layoutResId;
        mViewSpacingSpecified = false;
    }

    /**
     * Set the view to display in the dialog.
     */
    public void setView(View view) {
        mView = view;
        mViewLayoutResId = 0;
        mViewSpacingSpecified = false;
    }

    /**
     * Set the view to display in the dialog along with the spacing around that view
     */
    public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight,
            int viewSpacingBottom) {
        mView = view;
        mViewLayoutResId = 0;
        mViewSpacingSpecified = true;
        mViewSpacingLeft = viewSpacingLeft;
        mViewSpacingTop = viewSpacingTop;
        mViewSpacingRight = viewSpacingRight;
        mViewSpacingBottom = viewSpacingBottom;
    }

    /**
     * Sets a hint for the best button panel layout.
     */
    public void setButtonPanelLayoutHint(int layoutHint) {
        mButtonPanelLayoutHint = layoutHint;
    }

    /**
     * Sets a click listener or a message to be sent when the button is clicked.
     * You only need to pass one of {@code listener} or {@code msg}.
     *  @param whichButton Which button, can be one of
     *            {@link DialogInterface#BUTTON_POSITIVE},
     *            {@link DialogInterface#BUTTON_NEGATIVE}, or
     *            {@link DialogInterface#BUTTON_NEUTRAL}
     * @param text The text to display in button.
     * @param icon The icon to display in button.
     * @param listener The {@link DialogInterface.OnClickListener} to use.
     * @param msg The {@link Message} to be sent when clicked.
     */
    public void setButton(int whichButton, CharSequence text, Drawable icon,
                          DialogInterface.OnClickListener listener, Message msg) {

        if (msg == null && listener != null) {
            msg = mHandler.obtainMessage(whichButton, listener);
        }

        switch (whichButton) {

            case DialogInterface.BUTTON_POSITIVE:
                mButtonBundlePositive.buttonText = text;
                mButtonBundlePositive.buttonIcon = icon;
                mButtonBundlePositive.buttonMessage = msg;
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                mButtonBundleNegative.buttonText = text;
                mButtonBundleNegative.buttonIcon = icon;
                mButtonBundleNegative.buttonMessage = msg;
                break;

            case DialogInterface.BUTTON_NEUTRAL:
                mButtonBundleNeutral.buttonText = text;
                mButtonBundleNeutral.buttonIcon = icon;
                mButtonBundleNeutral.buttonMessage = msg;
                break;

            default:
                throw new IllegalArgumentException("Button does not exist");
        }
    }

    /**
     * Specifies the icon to display next to the alert title.
     *
     * @param resId the resource identifier of the drawable to use as the icon,
     *            or 0 for no icon
     */
    public void setIcon(int resId) {
        mIcon = null;
        mIconId = resId;

        if (mIconView != null) {
            if (resId != 0) {
                mIconView.setImageResource(mIconId);
            } else {
                mIconView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Specifies the icon to display next to the alert title.
     *
     * @param icon the drawable to use as the icon or null for no icon
     */
    public void setIcon(Drawable icon) {
        mIcon = icon;
        mIconId = 0;

        if (mIconView != null) {
            if (icon != null) {
                mIconView.setImageDrawable(icon);
            } else {
                mIconView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * @param attrId the attributeId of the theme-specific drawable
     * to resolve the resourceId for.
     *
     * @return resId the resourceId of the theme-specific drawable
     */
    public int getIconAttributeResId(int attrId) {
        TypedValue out = new TypedValue();
        mContext.getTheme().resolveAttribute(attrId, out, true);
        return out.resourceId;
    }

    public void setInverseBackgroundForced(boolean forceInverseBackground) {
        mForceInverseBackground = forceInverseBackground;
    }

    public ListView getListView() {
        return mListView;
    }

    public Button getButton(int whichButton) {
        switch (whichButton) {
            case DialogInterface.BUTTON_POSITIVE:
                return mButtonBundlePositive.textButton;
            case DialogInterface.BUTTON_NEGATIVE:
                return mButtonBundleNegative.textButton;
            case DialogInterface.BUTTON_NEUTRAL:
                return mButtonBundleNeutral.textButton;
            default:
                return null;
        }
    }

    public ImageButton getIconButton(int whichButton) {
        switch (whichButton) {
            case DialogInterface.BUTTON_POSITIVE:
                return mButtonBundlePositive.iconButton;
            case DialogInterface.BUTTON_NEGATIVE:
                return mButtonBundleNegative.iconButton;
            case DialogInterface.BUTTON_NEUTRAL:
                return mButtonBundleNeutral.iconButton;
            default:
                return null;
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mScrollView != null && mScrollView.executeKeyEvent(event);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mScrollView != null && mScrollView.executeKeyEvent(event);
    }

    /**
     * Hide all buttons.
     *
     * NOTE: This only work if the buttons are icon button.
     */
    public void hideButtons() {
        mWindow.getDecorView().removeCallbacks(buttonRestoreRunnable);
        mButtonBundlePositive.hideButton();
        mButtonBundleNegative.hideButton();
        mButtonBundleNeutral.hideButton();
    }

    /**
     * Set all buttons minimize.
     *
     * NOTE: This only work if the buttons are icon button.
     */
    public void minimizeButtons() {
        mWindow.getDecorView().removeCallbacks(buttonRestoreRunnable);
        mButtonBundlePositive.minimizeButton();
        mButtonBundleNegative.minimizeButton();
        mButtonBundleNeutral.minimizeButton();
    }

    /**
     * Show all buttons after a period of time.
     *
     * NOTE: This only work if the buttons are icon button.
     */
    public void showButtonsDelayed() {
        mWindow.getDecorView().removeCallbacks(buttonRestoreRunnable);
        long timeout = mContext.getResources()
                .getInteger(R.integer.design_time_action_idle_timeout);
        mWindow.getDecorView().postDelayed(buttonRestoreRunnable, timeout);
    }

    public void showButtons() {
        mWindow.getDecorView().removeCallbacks(buttonRestoreRunnable);
        mButtonBundlePositive.showButton();
        mButtonBundleNegative.showButton();
        mButtonBundleNeutral.showButton();
    }

    private Runnable buttonRestoreRunnable = new Runnable() {
        @Override
        public void run() {
            showButtons();
        }
    };

    private void setupDecor() {
        final View decor = mWindow.getDecorView();
        final View parent = mWindow.findViewById(R.id.parentPanel);
        if (parent != null && decor != null) {
            decor.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                    if (insets.isRound()) {
                        // TODO: Get the padding as a function of the window size.
                        int roundOffset = mContext.getResources().getDimensionPixelOffset(
                                R.dimen.alert_dialog_round_padding);
                        parent.setPadding(roundOffset, roundOffset, roundOffset, roundOffset);
                    }
                    return insets.consumeSystemWindowInsets();
                }
            });
            decor.setFitsSystemWindows(true);
            decor.requestApplyInsets();
        }
    }

    private void setupView() {
        final ViewGroup contentPanel = (ViewGroup) mWindow.findViewById(R.id.contentPanel);
        setupContent(contentPanel);
        final View buttonPanel = setupButtons();
        final boolean hasButtons = buttonPanel != null;

        final ViewGroup topPanel = (ViewGroup) mWindow.findViewById(R.id.topPanel);
        final TypedArray a = mContext.obtainStyledAttributes(
                null, R.styleable.AlertDialog, android.R.attr.alertDialogStyle, 0);
        final boolean hasTitle = setupTitle(topPanel);

        if (!hasButtons) {
            final View spacer = mWindow.findViewById(R.id.textSpacerNoButtons);
            if (spacer != null) {
                spacer.setVisibility(View.VISIBLE);
            }
//            mWindow.setCloseOnTouchOutsideIfNotSet(true);
        }

        final FrameLayout customPanel = (FrameLayout) mWindow.findViewById(R.id.customPanel);
        final View customView;
        if (mView != null) {
            customView = mView;
        } else if (mViewLayoutResId != 0) {
            final LayoutInflater inflater = LayoutInflater.from(mContext);
            customView = inflater.inflate(mViewLayoutResId, customPanel, false);
        } else {
            customView = null;
        }

        final boolean hasCustomView = customView != null;
        if (!hasCustomView || !canTextInput(customView)) {
            mWindow.setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }

        if (hasCustomView) {
            final FrameLayout custom = (FrameLayout) mWindow.findViewById(R.id.custom);
            custom.addView(customView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            if (mViewSpacingSpecified) {
                custom.setPadding(
                        mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight, mViewSpacingBottom);
            }

            if (mListView != null) {
                ((LinearLayout.LayoutParams) customPanel.getLayoutParams()).weight = 0;
            }
        } else {
            customPanel.setVisibility(View.GONE);
        }

        setBackground(a, topPanel, contentPanel, customPanel, buttonPanel, hasTitle, hasCustomView,
                hasButtons);
        a.recycle();
    }

    private boolean setupTitle(ViewGroup topPanel) {
        boolean hasTitle = true;

        if (mCustomTitleView != null) {
            // Add the custom title view directly to the topPanel layout
            LayoutParams lp = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

            topPanel.addView(mCustomTitleView, 0, lp);

            // Hide the title template
            View titleTemplate = mWindow.findViewById(R.id.title_template);
            titleTemplate.setVisibility(View.GONE);
        } else {
            mIconView = (ImageView) mWindow.findViewById(R.id.icon);

            final boolean hasTextTitle = !TextUtils.isEmpty(mTitle);
            if (hasTextTitle) {
                // Display the title if a title is supplied, else hide it.
                mTitleView = (TextView) mWindow.findViewById(R.id.alertTitle);
                mTitleView.setText(mTitle);

                // Do this last so that if the user has supplied any icons we
                // use them instead of the default ones. If the user has
                // specified 0 then make it disappear.
                if (mIconId != 0) {
                    mIconView.setImageResource(mIconId);
                } else if (mIcon != null) {
                    mIconView.setImageDrawable(mIcon);
                } else {
                    // Apply the padding from the icon to ensure the title is
                    // aligned correctly.
                    mTitleView.setPadding(mIconView.getPaddingLeft(),
                            mIconView.getPaddingTop(),
                            mIconView.getPaddingRight(),
                            mIconView.getPaddingBottom());
                    mIconView.setVisibility(View.GONE);
                }
            } else {
                // Hide the title template
                final View titleTemplate = mWindow.findViewById(R.id.title_template);
                titleTemplate.setVisibility(View.GONE);
                mIconView.setVisibility(View.GONE);
                topPanel.setVisibility(View.GONE);
                hasTitle = false;
            }
        }
        return hasTitle;
    }

    // SuppressLint for View.setOnScrollChangeListener, witch is a hidden API before API 23.
    @SuppressLint("NewApi")
    private void setupContent(ViewGroup contentPanel) {
        mScrollView = (SubscribedScrollView) mWindow.findViewById(R.id.scrollView);
        mScrollView.setFocusable(false);

        // Special case for users that only want to display a String
        mMessageView = (TextView) mWindow.findViewById(R.id.message);
        if (mMessageView == null) {
            return;
        }

        if (mMessage != null) {
            mMessageView.setText(mMessage);
        } else {
            mMessageView.setVisibility(View.GONE);
            mScrollView.removeView(mMessageView);

            if (mListView != null) {
                final ViewGroup scrollParent = (ViewGroup) mScrollView.getParent();
                final int childIndex = scrollParent.indexOfChild(mScrollView);
                scrollParent.removeViewAt(childIndex);
                scrollParent.addView(mListView, childIndex,
                        new LayoutParams(MATCH_PARENT, MATCH_PARENT));
            } else {
                contentPanel.setVisibility(View.GONE);
            }
        }

        // Set up scroll indicators (if present).
        final View indicatorUp = mWindow.findViewById(R.id.scrollIndicatorUp);
        final View indicatorDown = mWindow.findViewById(R.id.scrollIndicatorDown);
        if (indicatorUp != null || indicatorDown != null) {
            OnViewScrollListener onViewScrollListener = new OnViewScrollListener() {

                int scrollState = SubscribedScrollView.OnScrollListener.SCROLL_STATE_IDLE;
                boolean scrollDown = true;

                @Override
                public void onViewScrollStateChanged(View view, int state) {
                    scrollState = state;

                    if (scrollState == SubscribedScrollView.OnScrollListener.SCROLL_STATE_IDLE) {
                        showButtonsDelayed();
                    } else if (!scrollDown) {
                        hideButtons();
                    }
                }

                @Override
                public void onViewScroll(View view, int l, int t, int oldl, int oldt) {
                    manageScrollIndicators(view, indicatorUp, indicatorDown);

                    if (t == oldt) {
                        return;
                    }

                    boolean newScrollDown = (t - oldt) < 0;
                    if (newScrollDown && !scrollDown) {
                        showButtons();
                        scrollDown = true;
                    } else if (!newScrollDown && scrollDown &&
                            scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        hideButtons();
                        scrollDown = false;
                    }
                }
            };
            if (mMessage != null) {
                // We're just showing the ScrollView, set up listener.
                mScrollView.setOnScrollListener(onViewScrollListener);
                // Set up the indicators following layout.
                mScrollView.post(new Runnable() {
                     @Override
                     public void run() {
                             manageScrollIndicators(mScrollView, indicatorUp, indicatorDown);
                         }
                     });

            } else if (mListView != null) {
                // We're just showing the AbsListView, set up listener.
                mListView.setOnScrollListener(onViewScrollListener);
                // Set up the indicators following layout.
                mListView.post(new Runnable() {
                        @Override
                        public void run() {
                            manageScrollIndicators(mListView, indicatorUp, indicatorDown);
                        }
                    });
            } else {
                // We don't have any content to scroll, remove the indicators.
                if (indicatorUp != null) {
                    contentPanel.removeView(indicatorUp);
                }
                if (indicatorDown != null) {
                    contentPanel.removeView(indicatorDown);
                }
            }
        }
    }

    private static void manageScrollIndicators(View v, View upIndicator, View downIndicator) {
        if (upIndicator != null) {
            upIndicator.setVisibility(v.canScrollVertically(-1) ? View.VISIBLE : View.INVISIBLE);
        }
        if (downIndicator != null) {
            downIndicator.setVisibility(v.canScrollVertically(1) ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Nullable
    private View setupButtons() {
        setupButtonBundles();
        int textButtonCount = setupTextButtons();
        int iconButtonCount = setupIconButtons();
        boolean hasButtons = textButtonCount > 0 || iconButtonCount > 0;
        boolean useTextButtons = textButtonCount > iconButtonCount;

        final View textButtonPanel = mWindow.findViewById(R.id.textButtonPanel);
        final View iconButtonPanel = mWindow.findViewById(R.id.iconButtonPanel);
        final View buttonPanel;
        if (!hasButtons) {
            textButtonPanel.setVisibility(View.GONE);
            iconButtonPanel.setVisibility(View.GONE);
            buttonPanel = null;
        } else if (useTextButtons) {
            textButtonPanel.setVisibility(View.VISIBLE);
            iconButtonPanel.setVisibility(View.GONE);
            buttonPanel = textButtonPanel;
        } else {
            textButtonPanel.setVisibility(View.GONE);
            iconButtonPanel.setVisibility(View.VISIBLE);
            buttonPanel = iconButtonPanel;
        }

        return buttonPanel;
    }

    private void setupButtonBundles() {
        mButtonBundlePositive.setup(mWindow, R.id.textButton1, R.id.textSpace1,
                R.id.iconButton1, R.id.iconSpace1);
        mButtonBundleNegative.setup(mWindow, R.id.textButton2, R.id.textSpace2,
                R.id.iconButton2, R.id.iconSpace2);
        mButtonBundleNeutral.setup(mWindow, R.id.textButton3, R.id.textSpace3,
                R.id.iconButton3, R.id.iconSpace3);
    }

    private int setupTextButtons() {
        int BIT_BUTTON_POSITIVE = 1;
        int BIT_BUTTON_NEGATIVE = 2;
        int BIT_BUTTON_NEUTRAL = 4;
        int whichButtons = 0;
        if (mButtonBundlePositive.setupTextButton(mButtonHandler)) {
            whichButtons = whichButtons | BIT_BUTTON_POSITIVE;
        }
        if (mButtonBundleNegative.setupTextButton(mButtonHandler)) {
            whichButtons = whichButtons | BIT_BUTTON_NEGATIVE;
        }
        if (mButtonBundleNeutral.setupTextButton(mButtonHandler)) {
            whichButtons = whichButtons | BIT_BUTTON_NEUTRAL;
        }

        return Integer.bitCount(whichButtons);
    }

    private int setupIconButtons() {
        int BIT_BUTTON_POSITIVE = 1;
        int BIT_BUTTON_NEGATIVE = 2;
        int BIT_BUTTON_NEUTRAL = 4;
        int whichButtons = 0;
        if (mButtonBundlePositive.setupIconButton(mButtonHandler)) {
            whichButtons = whichButtons | BIT_BUTTON_POSITIVE;
        }
        if (mButtonBundleNegative.setupIconButton(mButtonHandler)) {
            whichButtons = whichButtons | BIT_BUTTON_NEGATIVE;
        }
        if (mButtonBundleNeutral.setupIconButton(mButtonHandler)) {
            whichButtons = whichButtons | BIT_BUTTON_NEUTRAL;
        }

        return Integer.bitCount(whichButtons);
    }

    private void setBackground(TypedArray a, View topPanel, View contentPanel, View customPanel,
            View buttonPanel, boolean hasTitle, boolean hasCustomView, boolean hasButtons) {
        int fullDark = 0;
        int topDark = 0;
        int centerDark = 0;
        int bottomDark = 0;
        int fullBright = 0;
        int topBright = 0;
        int centerBright = 0;
        int bottomBright = 0;
        int bottomMedium = 0;

//        // If the needsDefaultBackgrounds attribute is set, we know we're
//        // inheriting from a framework style.
//        final boolean needsDefaultBackgrounds = a.getBoolean(
//                R.styleable.AlertDialog_tic_needsDefaultBackgrounds, true);
//        if (needsDefaultBackgrounds) {
//            fullDark = R.drawable.popup_full_dark;
//            topDark = R.drawable.popup_top_dark;
//            centerDark = R.drawable.popup_center_dark;
//            bottomDark = R.drawable.popup_bottom_dark;
//            fullBright = R.drawable.popup_full_bright;
//            topBright = R.drawable.popup_top_bright;
//            centerBright = R.drawable.popup_center_bright;
//            bottomBright = R.drawable.popup_bottom_bright;
//            bottomMedium = R.drawable.popup_bottom_medium;
//        }
//
//        topBright = a.getResourceId(R.styleable.AlertDialog_tic_topBright, topBright);
//        topDark = a.getResourceId(R.styleable.AlertDialog_tic_topDark, topDark);
//        centerBright = a.getResourceId(R.styleable.AlertDialog_tic_centerBright, centerBright);
//        centerDark = a.getResourceId(R.styleable.AlertDialog_tic_centerDark, centerDark);

        /* We now set the background of all of the sections of the alert.
         * First collect together each section that is being displayed along
         * with whether it is on a light or dark background, then run through
         * them setting their backgrounds.  This is complicated because we need
         * to correctly use the full, top, middle, and bottom graphics depending
         * on how many views they are and where they appear.
         */

        final View[] views = new View[4];
        final boolean[] light = new boolean[4];
        View lastView = null;
        boolean lastLight = false;

        int pos = 0;
        if (hasTitle) {
            views[pos] = topPanel;
            light[pos] = false;
            pos++;
        }

        /* The contentPanel displays either a custom text message or
         * a ListView. If it's text we should use the dark background
         * for ListView we should use the light background. If neither
         * are there the contentPanel will be hidden so set it as null.
         */
        views[pos] = contentPanel.getVisibility() == View.GONE ? null : contentPanel;
        light[pos] = mListView != null;
        pos++;

        if (hasCustomView) {
            views[pos] = customPanel;
            light[pos] = mForceInverseBackground;
            pos++;
        }

        if (hasButtons) {
            views[pos] = buttonPanel;
            light[pos] = true;
        }

        boolean setView = false;
        for (pos = 0; pos < views.length; pos++) {
            final View v = views[pos];
            if (v == null) {
                continue;
            }

            if (lastView != null) {
                if (!setView) {
                    lastView.setBackgroundResource(lastLight ? topBright : topDark);
                } else {
                    lastView.setBackgroundResource(lastLight ? centerBright : centerDark);
                }
                setView = true;
            }

            lastView = v;
            lastLight = light[pos];
        }

//        if (lastView != null) {
//            if (setView) {
//                bottomBright = a.getResourceId(R.styleable.AlertDialog_tic_bottomBright, bottomBright);
//                bottomMedium = a.getResourceId(R.styleable.AlertDialog_tic_bottomMedium, bottomMedium);
//                bottomDark = a.getResourceId(R.styleable.AlertDialog_tic_bottomDark, bottomDark);
//
//                // ListViews will use the Bright background, but buttons use the
//                // Medium background.
//                lastView.setBackgroundResource(
//                        lastLight ? (hasButtons ? bottomMedium : bottomBright) : bottomDark);
//            } else {
//                fullBright = a.getResourceId(R.styleable.AlertDialog_tic_fullBright, fullBright);
//                fullDark = a.getResourceId(R.styleable.AlertDialog_tic_fullDark, fullDark);
//
//                lastView.setBackgroundResource(lastLight ? fullBright : fullDark);
//            }
//        }

        final ListView listView = mListView;
        if (listView != null && mAdapter != null) {
            listView.setAdapter(mAdapter);
            final int checkedItem = mCheckedItem;
            if (checkedItem > -1) {
                listView.setItemChecked(checkedItem, true);
                listView.setSelection(checkedItem);
            }
        }
    }

    public static class RecycleListView extends ListView {
        boolean mRecycleOnMeasure = true;

        public RecycleListView(Context context) {
            super(context);
        }

        public RecycleListView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public RecycleListView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        public RecycleListView(
                Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

        protected boolean recycleOnMeasure() {
            return mRecycleOnMeasure;
        }
    }

    public static class AlertParams {
        public final Context mContext;
        public final LayoutInflater mInflater;

        public int mIconId = 0;
        public Drawable mIcon;
        public int mIconAttrId = 0;
        public CharSequence mTitle;
        public View mCustomTitleView;
        public CharSequence mMessage;
        public CharSequence mPositiveButtonText;
        public Drawable mPositiveButtonIcon;
        public DialogInterface.OnClickListener mPositiveButtonListener;
        public CharSequence mNegativeButtonText;
        public Drawable mNegativeButtonIcon;
        public DialogInterface.OnClickListener mNegativeButtonListener;
        public CharSequence mNeutralButtonText;
        public Drawable mNeutralButtonIcon;
        public DialogInterface.OnClickListener mNeutralButtonListener;
        public boolean mCancelable;
        public DialogInterface.OnCancelListener mOnCancelListener;
        public DialogInterface.OnDismissListener mOnDismissListener;
        public DialogInterface.OnKeyListener mOnKeyListener;
        public CharSequence[] mItems;
        public ListAdapter mAdapter;
        public DialogInterface.OnClickListener mOnClickListener;
        public int mViewLayoutResId;
        public View mView;
        public int mViewSpacingLeft;
        public int mViewSpacingTop;
        public int mViewSpacingRight;
        public int mViewSpacingBottom;
        public boolean mViewSpacingSpecified = false;
        public boolean[] mCheckedItems;
        public boolean mIsMultiChoice;
        public boolean mIsSingleChoice;
        public int mCheckedItem = -1;
        public DialogInterface.OnMultiChoiceClickListener mOnCheckboxClickListener;
        public Cursor mCursor;
        public String mLabelColumn;
        public String mIsCheckedColumn;
        public boolean mForceInverseBackground;
        public AdapterView.OnItemSelectedListener mOnItemSelectedListener;
        public OnPrepareListViewListener mOnPrepareListViewListener;
        public boolean mRecycleOnMeasure = true;

        /**
         * Interface definition for a callback to be invoked before the ListView
         * will be bound to an adapter.
         */
        public interface OnPrepareListViewListener {

            /**
             * Called before the ListView is bound to an adapter.
             * @param listView The ListView that will be shown in the dialog.
             */
            void onPrepareListView(ListView listView);
        }

        public AlertParams(Context context) {
            mContext = context;
            mCancelable = true;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void apply(AlertController dialog) {
            if (mCustomTitleView != null) {
                dialog.setCustomTitle(mCustomTitleView);
            } else {
                if (mTitle != null) {
                    dialog.setTitle(mTitle);
                }
                if (mIcon != null) {
                    dialog.setIcon(mIcon);
                }
                if (mIconId != 0) {
                    dialog.setIcon(mIconId);
                }
                if (mIconAttrId != 0) {
                    dialog.setIcon(dialog.getIconAttributeResId(mIconAttrId));
                }
            }
            if (mMessage != null) {
                dialog.setMessage(mMessage);
            }
            if (mPositiveButtonText != null || mPositiveButtonIcon != null) {
                dialog.setButton(DialogInterface.BUTTON_POSITIVE, mPositiveButtonText,
                        mPositiveButtonIcon, mPositiveButtonListener, null);
            }
            if (mNegativeButtonText != null || mNegativeButtonIcon != null) {
                dialog.setButton(DialogInterface.BUTTON_NEGATIVE, mNegativeButtonText,
                        mNegativeButtonIcon, mNegativeButtonListener, null);
            }
            if (mNeutralButtonText != null || mNeutralButtonIcon != null) {
                dialog.setButton(DialogInterface.BUTTON_NEUTRAL, mNeutralButtonText,
                        mNeutralButtonIcon, mNeutralButtonListener, null);
            }
            if (mForceInverseBackground) {
                dialog.setInverseBackgroundForced(true);
            }
            // For a list, the client can either supply an array of items or an
            // adapter or a cursor
            if ((mItems != null) || (mCursor != null) || (mAdapter != null)) {
                createListView(dialog);
            }
            if (mView != null) {
                if (mViewSpacingSpecified) {
                    dialog.setView(mView, mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight,
                            mViewSpacingBottom);
                } else {
                    dialog.setView(mView);
                }
            } else if (mViewLayoutResId != 0) {
                dialog.setView(mViewLayoutResId);
            }

            /*
            dialog.setCancelable(mCancelable);
            dialog.setOnCancelListener(mOnCancelListener);
            if (mOnKeyListener != null) {
                dialog.setOnKeyListener(mOnKeyListener);
            }
            */
        }

        private void createListView(final AlertController dialog) {
            final RecycleListView listView = (RecycleListView)
                    mInflater.inflate(dialog.mListLayout, null);
            ListAdapter adapter;

            if (mIsMultiChoice) {
                if (mCursor == null) {
                    adapter = new ArrayAdapter<CharSequence>(
                            mContext, dialog.mMultiChoiceItemLayout, android.R.id.text1, mItems) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            if (mCheckedItems != null) {
                                boolean isItemChecked = mCheckedItems[position];
                                if (isItemChecked) {
                                    listView.setItemChecked(position, true);
                                }
                            }
                            return view;
                        }
                    };
                } else {
                    adapter = new CursorAdapter(mContext, mCursor, false) {
                        private final int mLabelIndex;
                        private final int mIsCheckedIndex;

                        {
                            final Cursor cursor = getCursor();
                            mLabelIndex = cursor.getColumnIndexOrThrow(mLabelColumn);
                            mIsCheckedIndex = cursor.getColumnIndexOrThrow(mIsCheckedColumn);
                        }

                        @Override
                        public void bindView(View view, Context context, Cursor cursor) {
                            CheckedTextView text = (CheckedTextView) view.findViewById(android.R.id.text1);
                            text.setText(cursor.getString(mLabelIndex));
                            listView.setItemChecked(cursor.getPosition(),
                                    cursor.getInt(mIsCheckedIndex) == 1);
                        }

                        @Override
                        public View newView(Context context, Cursor cursor, ViewGroup parent) {
                            return mInflater.inflate(dialog.mMultiChoiceItemLayout,
                                    parent, false);
                        }

                    };
                }
            } else {
                int layout = mIsSingleChoice
                        ? dialog.mSingleChoiceItemLayout : dialog.mListItemLayout;
                if (mCursor == null) {
                    adapter = (mAdapter != null) ? mAdapter
                            : new CheckedItemAdapter(mContext, layout, android.R.id.text1, mItems);
                } else {
                    adapter = new SimpleCursorAdapter(mContext, layout,
                            mCursor, new String[]{mLabelColumn}, new int[]{android.R.id.text1});
                }
            }

            if (mOnPrepareListViewListener != null) {
                mOnPrepareListViewListener.onPrepareListView(listView);
            }

            /* Don't directly set the adapter on the ListView as we might
             * want to add a footer to the ListView later.
             */
            dialog.mAdapter = adapter;
            dialog.mCheckedItem = mCheckedItem;

            if (mOnClickListener != null) {
                listView.setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                        mOnClickListener.onClick(dialog.mDialogInterface, position);
                        if (!mIsSingleChoice) {
                            dialog.mDialogInterface.dismiss();
                        }
                    }
                });
            } else if (mOnCheckboxClickListener != null) {
                listView.setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                        if (mCheckedItems != null) {
                            mCheckedItems[position] = listView.isItemChecked(position);
                        }
                        mOnCheckboxClickListener.onClick(
                                dialog.mDialogInterface, position, listView.isItemChecked(position));
                    }
                });
            }

            // Attach a given OnItemSelectedListener to the ListView
            if (mOnItemSelectedListener != null) {
                listView.setOnItemSelectedListener(mOnItemSelectedListener);
            }

            if (mIsSingleChoice) {
                listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            } else if (mIsMultiChoice) {
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            }
            listView.mRecycleOnMeasure = mRecycleOnMeasure;
            dialog.mListView = listView;
        }
    }

    private static class CheckedItemAdapter extends ArrayAdapter<CharSequence> {
        public CheckedItemAdapter(Context context, int resource, int textViewResourceId,
                CharSequence[] objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private static class ButtonBundle {
        private Button textButton;
        private Space textSpace;
        private FloatingActionButton iconButton;
        private Space iconSpace;
        private CharSequence buttonText;
        private Drawable buttonIcon;
        private Message buttonMessage;

        public void setup(Window window,
                          @IdRes int textButtonId, @IdRes int textSpaceId,
                          @IdRes int iconButtonId, @IdRes int iconSpaceId) {
            textButton = (Button) window.findViewById(textButtonId);
            textSpace = (Space) window.findViewById(textSpaceId);
            iconButton = (FloatingActionButton) window.findViewById(iconButtonId);
            iconSpace = (Space) window.findViewById(iconSpaceId);
        }

        public Message messageForButton(View v) {
            if ((v == textButton || v == iconButton) && buttonMessage != null) {
                return Message.obtain(buttonMessage);
            }

            return null;
        }

        public boolean setupTextButton(@Nullable View.OnClickListener l) {
            textButton.setOnClickListener(l);

            if (TextUtils.isEmpty(buttonText)) {
                textButton.setVisibility(View.GONE);
                textSpace.setVisibility(View.GONE);
                return false;
            } else {
                textButton.setText(buttonText);
                textButton.setVisibility(View.VISIBLE);
                textSpace.setVisibility(View.INVISIBLE);
                return true;
            }
        }

        public boolean setupIconButton(@Nullable View.OnClickListener l) {
            iconButton.setOnClickListener(l);

            if (buttonIcon == null) {
                iconButton.setVisibility(View.GONE);
                iconSpace.setVisibility(View.GONE);
                return false;
            } else {
                iconButton.setImageDrawable(buttonIcon);
                iconButton.setVisibility(View.VISIBLE);
                iconSpace.setVisibility(View.INVISIBLE);
                return true;
            }
        }

        public void hideButton() {
            if (hasIconButton()) {
                iconButton.hide();
            }
        }

        public void minimizeButton() {
            if (hasIconButton()) {
                iconButton.minimize();
            }
        }

        public void showButton() {
            if (hasIconButton()) {
                iconButton.show();
            }
        }

        private boolean hasIconButton() {
            return buttonIcon != null || iconButton.getVisibility() == View.VISIBLE;
        }
    }


    private abstract class OnViewScrollListener implements
            AbsListView.OnScrollListener, SubscribedScrollView.OnScrollListener {

        private int oldScrollY = 0;
        private int oldScrollX = 0;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            onViewScrollStateChanged(view, scrollState);
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            int scrollX = view.getScrollX();
            int scrollY = view.getScrollY();
            onViewScroll(view, scrollX, scrollY, oldScrollX, oldScrollY);
            oldScrollX = scrollX;
            oldScrollY = scrollY;
        }

        @Override
        public void onScrollStateChanged(SubscribedScrollView view, int scrollState) {
            onViewScrollStateChanged(view, scrollState);
        }

        @Override
        public void onScroll(SubscribedScrollView view, int l, int t, int oldl, int oldt) {
            onViewScroll(view, l, t, oldl, oldt);
        }


        public abstract void onViewScrollStateChanged(View view, int scrollState);

        public abstract void onViewScroll(View view, int l, int t, int oldl, int oldt);
    }
}