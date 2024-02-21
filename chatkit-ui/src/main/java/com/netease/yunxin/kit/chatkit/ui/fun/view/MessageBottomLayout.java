// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.chatkit.ui.fun.view;

import static com.netease.yunxin.kit.chatkit.ui.ChatKitUIConstant.LIB_TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.netease.nimlib.sdk.media.record.AudioRecorder;
import com.netease.nimlib.sdk.media.record.IAudioRecordCallback;
import com.netease.nimlib.sdk.media.record.RecordType;
import com.netease.nimlib.sdk.msg.attachment.MsgAttachment;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.ui.R;
import com.netease.yunxin.kit.chatkit.ui.common.MessageHelper;
import com.netease.yunxin.kit.chatkit.ui.custom.StickerAttachment;
import com.netease.yunxin.kit.chatkit.ui.databinding.FunChatMessageBottomViewBinding;
import com.netease.yunxin.kit.chatkit.ui.fun.FunAudioRecordDialog;
import com.netease.yunxin.kit.chatkit.ui.fun.view.input.FunBottomActionFactory;
import com.netease.yunxin.kit.chatkit.ui.interfaces.IMessageProxy;
import com.netease.yunxin.kit.chatkit.ui.model.ChatMessageBean;
import com.netease.yunxin.kit.chatkit.ui.view.IItemActionListener;
import com.netease.yunxin.kit.chatkit.ui.view.ait.AitManager;
import com.netease.yunxin.kit.chatkit.ui.view.ait.AitTextChangeListener;
import com.netease.yunxin.kit.chatkit.ui.view.emoji.IEmojiSelectedListener;
import com.netease.yunxin.kit.chatkit.ui.view.input.ActionConstants;
import com.netease.yunxin.kit.chatkit.ui.view.input.ActionsPanel;
import com.netease.yunxin.kit.chatkit.ui.view.input.InputProperties;
import com.netease.yunxin.kit.chatkit.ui.view.input.InputState;
import com.netease.yunxin.kit.chatkit.ui.view.message.audio.ChatMessageAudioControl;
import com.netease.yunxin.kit.common.ui.action.ActionItem;
import com.netease.yunxin.kit.common.ui.dialog.BottomChoiceDialog;
import com.netease.yunxin.kit.common.ui.utils.Permission;
import com.netease.yunxin.kit.common.ui.utils.ToastX;
import com.netease.yunxin.kit.common.utils.KeyboardUtils;
import com.netease.yunxin.kit.common.utils.NetworkUtils;
import com.netease.yunxin.kit.common.utils.PermissionUtils;
import com.netease.yunxin.kit.common.utils.XKitUtils;
import com.netease.yunxin.kit.corekit.im.IMKitClient;
import java.io.File;
import java.util.List;

public class MessageBottomLayout extends FrameLayout
    implements IAudioRecordCallback, AitTextChangeListener, IItemActionListener {
  public static final String TAG = "MessageBottomLayout";
  private static final long SHOW_DELAY_TIME = 200;
  private FunChatMessageBottomViewBinding mBinding;
  private IMessageProxy mProxy;
  private String mEdieNormalHint = "";
  private boolean mMute = false;
  private InputProperties inputProperties;

  ChatMessageBean replyMessage;

  private final ActionsPanel mActionsPanel = new ActionsPanel();
  private AitManager aitTextWatcher;

  private boolean isKeyboardShow = false;
  private InputState mInputState = InputState.none;
  private IEmojiSelectedListener emojiSelectedListener;

  private FunAudioRecordDialog recordDialog;

  private boolean inRecordOpView;

  private AudioRecorder mAudioRecorder;

  private final int recordMaxDuration = 60;

  private boolean canRender = true;

  private boolean keepRichEt = false;

  public MessageBottomLayout(@NonNull Context context) {
    this(context, null);
  }

  public MessageBottomLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MessageBottomLayout(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initView();
  }

  @SuppressLint("ClickableViewAccessibility")
  public void init(IMessageProxy proxy) {
    mProxy = proxy;
    mBinding.inputEmojiRb.setOnClickListener(v -> switchEmoji());
    mBinding.inputMoreRb.setOnClickListener(v -> switchMore());
    mBinding.inputAudioRb.setOnClickListener(v -> switchRecord());

    mBinding.inputAudioTv.setOnTouchListener(
        (v, event) -> {
          ALog.d(LIB_TAG, TAG, "inputAudioTv OnTouch, event:" + event.getAction());
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mProxy.hasPermission(Manifest.permission.RECORD_AUDIO)) {
              showAudioInputDialog();
            } else {
              return false;
            }
          } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (recordDialog != null) {
              int x = (int) event.getRawX();
              int y = (int) event.getRawY();
              inRecordOpView = recordDialog.getOpViewRect().contains(x, y);
              if (inRecordOpView) {
                recordDialog.showCancelView();
              } else {
                recordDialog.showRecordingView();
              }
            }
          } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (recordDialog != null) {
              dismissAudioInputDialog(inRecordOpView);
            }
          }
          return true;
        });
    emojiSelectedListener =
        new IEmojiSelectedListener() {
          @Override
          public void onEmojiSelected(String key) {
            Editable mEditable = mBinding.inputEt.getText();
            if (key.equals("/DEL")) {
              mBinding.inputEt.dispatchKeyEvent(
                  new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            } else {
              int start = mBinding.inputEt.getSelectionStart();
              int end = mBinding.inputEt.getSelectionEnd();
              start = Math.max(start, 0);
              mEditable.replace(start, end, key);
            }
          }

          @Override
          public void onStickerSelected(String categoryName, String stickerName) {
            MsgAttachment attachment = new StickerAttachment(categoryName, stickerName);
            mProxy.sendCustomMessage(
                attachment, getContext().getString(R.string.chat_message_custom_sticker));
          }

          @Override
          public void onEmojiSendClick() {
            sendText(replyMessage);
          }
        };
    mEdieNormalHint = getContext().getResources().getString(R.string.fun_chat_input_hint_tips);
    mBinding.replyLayout.setVisibility(GONE);
    mBinding.inputEt.setOnFocusChangeListener(
        (v, hasFocus) ->
            mProxy.onTypeStateChange(!TextUtils.isEmpty(mBinding.inputEt.getText()) && hasFocus));
  }

  public FunChatMessageBottomViewBinding getViewBinding() {
    return mBinding;
  }

  @Override
  public void onClick(View view, int position, ActionItem item) {
    ALog.d(TAG, "action click, inputState:" + mInputState);
    if (mProxy != null && mProxy.onActionClick(view, item.getAction())) {
      return;
    }
    switch (item.getAction()) {
      case ActionConstants.ACTION_TYPE_RECORD:
        switchRecord();
        break;
      case ActionConstants.ACTION_TYPE_EMOJI:
        switchEmoji();
        break;
      case ActionConstants.ACTION_TYPE_ALBUM:
        onAlbumClick();
        break;
      case ActionConstants.ACTION_TYPE_FILE:
        onFileClick();
        break;
      case ActionConstants.ACTION_TYPE_MORE:
        switchMore();
        break;
      case ActionConstants.ACTION_TYPE_CAMERA:
        onCameraClick();
        break;
      case ActionConstants.ACTION_TYPE_LOCATION:
        onLocationClick();
        break;
      case ActionConstants.ACTION_TYPE_VIDEO_CALL:
        onCallClick();
        break;
      default:
        mProxy.onCustomAction(view, item.getAction());
        break;
    }
  }

  public void setAitTextWatcher(AitManager aitTextWatcher) {
    this.aitTextWatcher = aitTextWatcher;
  }

  public String getInputHit() {
    return mEdieNormalHint;
  }

  public void updateInputHintInfo(String content) {
    mEdieNormalHint = content;
    mBinding.inputEt.setHint(mEdieNormalHint);
  }

  public void showAudioInputDialog() {

    if (mAudioRecorder == null) {
      mAudioRecorder = new AudioRecorder(getContext(), RecordType.AAC, recordMaxDuration, this);
    }

    recordDialog = new FunAudioRecordDialog(getContext());
    if (!recordDialog.isShowing()) {
      recordDialog.show(recordMaxDuration);
      recordDialog.showRecordingView();

      //record
      if (getContext() instanceof Activity) {
        ((Activity) getContext())
            .getWindow()
            .setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
      mAudioRecorder.startRecord();
    }
  }

  public void dismissAudioInputDialog(boolean isCancel) {

    if (recordDialog != null && recordDialog.isShowing()) {
      recordDialog.dismiss();

      ALog.d(TAG, "endAudioRecord:");
      if (getContext() instanceof Activity) {
        ((Activity) getContext())
            .getWindow()
            .setFlags(0, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
      mAudioRecorder.completeRecord(isCancel);
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private void initView() {
    mBinding =
        FunChatMessageBottomViewBinding.inflate(LayoutInflater.from(getContext()), this, true);
    getViewTreeObserver()
        .addOnGlobalLayoutListener(
            () -> {
              if (KeyboardUtils.isKeyboardShow((Activity) getContext())) {
                if (!isKeyboardShow) {
                  onKeyboardShow();
                  isKeyboardShow = true;
                }
              } else {
                if (isKeyboardShow) {
                  onKeyboardHide();
                  isKeyboardShow = false;
                }
              }
            });
    // input view
    mBinding.inputEt.addTextChangedListener(msgInputTextWatcher);

    mBinding.inputEt.setOnTouchListener(
        (v, event) -> {
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            switchInput();
          }
          return false;
        });
    mBinding.inputEt.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_SEND) {
            sendText(replyMessage);
          }
          return true;
        });
    mBinding.chatRichEt.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_SEND) {
            sendText(replyMessage);
          }
          return true;
        });

    mBinding.emojiPickerView.setWithSticker(true);
    // 多行消息设置点击事件
    mBinding.chatRichEt.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (TextUtils.isEmpty(s.toString()) && !keepRichEt) {
              mBinding.chatRichEt.setVisibility(GONE);
              mBinding.inputEt.requestFocus();
            }
            keepRichEt = false;
          }
        });
    loadConfig();
  }

  private TextWatcher msgInputTextWatcher =
      new TextWatcher() {
        private int start;
        private int count;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
          if (!canRender) {
            return;
          }
          if (aitTextWatcher != null) {
            aitTextWatcher.beforeTextChanged(s, start, count, after);
          }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          if (!canRender) {
            return;
          }
          this.start = start;
          this.count = count;
          if (aitTextWatcher != null) {
            aitTextWatcher.onTextChanged(s, start, before, count);
          }
          if (mProxy != null) {
            mProxy.onTypeStateChange(!TextUtils.isEmpty(s));
          }
        }

        @Override
        public void afterTextChanged(Editable s) {
          if (!canRender) {
            canRender = true;
            return;
          }
          SpannableString spannableString = new SpannableString(s);
          if (MessageHelper.replaceEmoticons(getContext(), spannableString, start, count)) {
            canRender = false;
            mBinding.inputEt.setText(spannableString);
            mBinding.inputEt.setSelection(spannableString.length());
          }

          if (aitTextWatcher != null) {
            aitTextWatcher.afterTextChanged(s);
          }
          if (TextUtils.isEmpty(s.toString())) {
            mBinding.inputEt.setHint(mEdieNormalHint);
          }
        }
      };

  public void clearInputEditTextChange() {
    mBinding.inputEt.removeTextChangedListener(msgInputTextWatcher);
  }

  public void sendText(ChatMessageBean replyMessage) {

    String msg = mBinding.inputEt.getEditableText().toString();
    String title = mBinding.chatRichEt.getEditableText().toString();
    if (mProxy != null) {
      if (!TextUtils.isEmpty(title) && TextUtils.getTrimmedLength(title) > 0) {
        if (mProxy.sendRichTextMessage(title, msg, replyMessage)) {
          mBinding.chatRichEt.setText("");
          mBinding.inputEt.setText("");
          clearReplyMsg();
        } else {
          clearInput();
        }
      } else {
        if (mProxy.sendTextMessage(msg, replyMessage)) {
          mBinding.inputEt.setText("");
          clearReplyMsg();
        } else {
          clearInput();
        }
      }
    }
  }

  public void clearInput() {
    String msg = mBinding.inputEt.getEditableText().toString();
    String title = mBinding.chatRichEt.getEditableText().toString();
    if (TextUtils.getTrimmedLength(msg) < 1) {
      mBinding.inputEt.setText("");
    }
    if (TextUtils.getTrimmedLength(title) < 1) {
      keepRichEt = true;
      mBinding.chatRichEt.setText("");
    }
  }

  public void hideAndClearRichInput() {
    mBinding.chatRichEt.setText("");
    mBinding.chatRichEt.setVisibility(GONE);
  }

  public void hideCurrentInput() {
    if (mInputState == InputState.input) {
      hideKeyboard();
    } else if (mInputState == InputState.voice) {
      recordShow(false, 0);
    } else if (mInputState == InputState.emoji) {
      emojiShow(false, 0);
    } else if (mInputState == InputState.more) {
      morePanelShow(false, 0);
    }
  }

  public void setRichTextSwitchListener(OnClickListener listener) {
    mBinding.chatMsgInputSwitchLayout.setOnClickListener(listener);
  }

  // 获取富文本标题
  public String getRichInputTitle() {
    return mBinding.chatRichEt.getText().toString();
  }

  // 获取富文本内容，非富文本状态则返回普通小心文本
  public String getRichInputContent() {
    return mBinding.inputEt.getText().toString();
  }

  public void switchRichInput(boolean titleForces, String title, String content) {

    hideCurrentInput();
    mInputState = InputState.input;
    if (!TextUtils.isEmpty(title)) {
      mBinding.chatRichEt.setVisibility(VISIBLE);
      MessageHelper.identifyFaceExpression(
          getContext(), mBinding.chatRichEt, title, ImageSpan.ALIGN_BOTTOM);
    } else {
      mBinding.chatRichEt.setText("");
      mBinding.chatRichEt.setVisibility(GONE);
    }
    MessageHelper.identifyExpressionForEditMsg(
        getContext(),
        mBinding.inputEt,
        content,
        aitTextWatcher != null ? aitTextWatcher.getAitContactsModel() : null);
    if (!TextUtils.isEmpty(content)) {
      mBinding.inputEt.setSelection(content.length());
    }
    mBinding.inputEt.addTextChangedListener(msgInputTextWatcher);
  }

  public void switchInput() {
    if (mInputState == InputState.input) {
      return;
    }
    hideCurrentInput();
    showKeyboard();
    updateState(InputState.input);
  }

  public void switchRecord() {
    if (mInputState == InputState.voice) {
      recordShow(false, 0);
      updateState(InputState.input);
      return;
    }
    recordShow(true, 0);
    hideCurrentInput();
    updateState(InputState.voice);
  }

  public void richInputShow(boolean show, long delay) {
    postDelayed(
        () -> {
          mBinding.chatRichEt.setVisibility(show ? VISIBLE : GONE);
          if (show) {
            mBinding.chatRichEt.requestFocus();
          }
        },
        delay);
  }

  public void recordShow(boolean show, long delay) {
    mBinding.inputAudioTv.setVisibility(show ? VISIBLE : GONE);
    mBinding.inputEt.setVisibility(show ? GONE : VISIBLE);
    mBinding.chatMsgInputSwitchLayout.setVisibility(show ? GONE : VISIBLE);
  }

  public void switchEmoji() {
    if (mInputState == InputState.emoji) {
      emojiShow(false, 0);
      updateState(InputState.none);
      return;
    }
    emojiShow(true, 0);
    hideCurrentInput();
    updateState(InputState.emoji);
  }

  private void updateState(InputState state) {
    mInputState = state;
    mBinding.inputMoreRb.setBackgroundResource(R.drawable.fun_ic_chat_input_more_selector);
    mBinding.inputMoreRb.setChecked(mInputState == InputState.more);
    mBinding.inputEmojiRb.setBackgroundResource(R.drawable.fun_ic_chat_input_emoji_selector);
    mBinding.inputEmojiRb.setChecked(mInputState == InputState.emoji);
    //防止滑动的时候，语音切换到输入框
    if (state != InputState.none) {
      mBinding.inputAudioRb.setBackgroundResource(R.drawable.fun_ic_chat_input_audio_selector);
      mBinding.inputAudioRb.setChecked(mInputState == InputState.voice);
    }
  }

  public void emojiShow(boolean show, long delay) {
    postDelayed(
        () -> {
          mBinding.emojiPickerView.setVisibility(show ? VISIBLE : GONE);
          if (show) {
            mBinding.emojiPickerView.show(emojiSelectedListener);
          }
        },
        delay);
  }

  public void switchMore() {
    if (mInputState == InputState.more) {
      morePanelShow(false, 0);
      updateState(InputState.none);
      return;
    }
    morePanelShow(true, 0);
    hideCurrentInput();
    updateState(InputState.more);
  }

  public void morePanelShow(boolean show, long delay) {
    // init more panel
    if (!mActionsPanel.hasInit() && show) {
      mActionsPanel.init(
          mBinding.actionsPanelVp,
          FunBottomActionFactory.assembleInputMoreActions(mProxy.getSessionType()),
          this);
    }
    postDelayed(() -> mBinding.actionsPanelVp.setVisibility(show ? VISIBLE : GONE), delay);
  }

  public void onAlbumClick() {
    if (mInputState == InputState.input) {
      hideKeyboard();
      postDelayed(() -> mProxy.pickMedia(), SHOW_DELAY_TIME);
    } else {
      mProxy.pickMedia();
    }
  }

  public void onCameraClick() {
    BottomChoiceDialog dialog =
        new BottomChoiceDialog(
            this.getContext(), FunBottomActionFactory.assembleTakeShootActions());
    dialog.setOnChoiceListener(
        new BottomChoiceDialog.OnChoiceListener() {
          @Override
          public void onChoice(@NonNull String type) {

            if (!XKitUtils.getApplicationContext()
                .getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
              ToastX.showShortToast(R.string.chat_message_camera_unavailable);
              return;
            }
            switch (type) {
              case ActionConstants.ACTION_TYPE_TAKE_PHOTO:
                mProxy.takePicture();
                break;
              case ActionConstants.ACTION_TYPE_TAKE_VIDEO:
                mProxy.captureVideo();
                break;
              default:
                break;
            }
          }

          @Override
          public void onCancel() {}
        });
    dialog.show();
  }

  public void onCallClick() {
    BottomChoiceDialog dialog =
        new BottomChoiceDialog(
            this.getContext(), FunBottomActionFactory.assembleVideoCallActions());
    dialog.setOnChoiceListener(
        new BottomChoiceDialog.OnChoiceListener() {
          @Override
          public void onChoice(@NonNull String type) {
            if (!NetworkUtils.isConnected()) {
              ToastX.showShortToast(R.string.chat_network_error_tip);
              return;
            }
            if (!XKitUtils.getApplicationContext()
                .getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
              ToastX.showShortToast(R.string.chat_message_camera_unavailable);
              return;
            }
            switch (type) {
              case ActionConstants.ACTION_TYPE_VIDEO_CALL_ACTION:
                mProxy.videoCall();
                break;
              case ActionConstants.ACTION_TYPE_AUDIO_CALL_ACTION:
                mProxy.audioCall();
                break;
              default:
                break;
            }
          }

          @Override
          public void onCancel() {}
        });
    dialog.show();
  }

  public void onLocationClick() {
    String[] permissions = {
      Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
    };
    if (PermissionUtils.hasPermissions(IMKitClient.getApplicationContext(), permissions)) {
      mProxy.sendLocationLaunch();
    } else {
      Permission.requirePermissions(IMKitClient.getApplicationContext(), permissions)
          .request(
              new Permission.PermissionCallback() {
                @Override
                public void onGranted(List<String> permissionsGranted) {
                  mProxy.sendLocationLaunch();
                }

                @Override
                public void onDenial(
                    List<String> permissionsDenial, List<String> permissionDenialForever) {
                  Toast.makeText(getContext(), R.string.permission_default, Toast.LENGTH_SHORT)
                      .show();
                }

                @Override
                public void onException(Exception exception) {
                  Toast.makeText(getContext(), R.string.permission_default, Toast.LENGTH_SHORT)
                      .show();
                }
              });
    }
  }

  public void onFileClick() {
    if (mInputState == InputState.input) {
      hideKeyboard();
      postDelayed(() -> mProxy.sendFile(), SHOW_DELAY_TIME);
    } else {
      mProxy.sendFile();
      clearReplyMsg();
    }
  }

  public void setMute(boolean mute) {
    if (mute != mMute) {
      mMute = mute;
      mBinding.inputEt.setEnabled(!mute);
      mBinding.inputMuteTv.setVisibility(mute ? VISIBLE : GONE);
      mBinding.inputEt.setText("");
      mBinding.chatRichEt.setText("");
      if (mute) {
        collapse(true);
      }
      mBinding.inputLayout.setBackgroundResource(mute ? R.color.color_e3e4e4 : R.color.color_white);
      mBinding.inputAudioRb.setEnabled(!mute);
      mBinding.inputAudioRb.setAlpha(mute ? 0.5f : 1f);
      mBinding.inputEmojiRb.setEnabled(!mute);
      mBinding.inputEmojiRb.setAlpha(mute ? 0.5f : 1f);
      mBinding.inputMoreRb.setEnabled(!mute);
      mBinding.inputMoreRb.setAlpha(mute ? 0.5f : 1f);
    }
  }

  public boolean isMute() {
    return mMute;
  }

  public void collapse(boolean immediately) {
    if (mInputState == InputState.none) {
      return;
    }
    hideAllInputLayout(immediately);
  }

  public void setReplyMessage(ChatMessageBean messageBean) {
    this.replyMessage = messageBean;
    mBinding.replyLayout.setVisibility(VISIBLE);
    String tips = MessageHelper.getReplyMessageTips(messageBean.getMessageData());
    tips = String.format(getContext().getString(R.string.chat_message_reply_someone), tips);
    MessageHelper.identifyFaceExpression(
        getContext(),
        mBinding.replyContentTv,
        tips,
        ImageSpan.ALIGN_CENTER,
        MessageHelper.SMALL_SCALE);
    mBinding.replyCloseIv.setOnClickListener(v -> clearReplyMsg());
    switchInput();
  }

  public void setReEditMessage(String msgContent) {
    mBinding.inputEt.setText(msgContent);
    mBinding.inputEt.requestFocus();
    mBinding.inputEt.setSelection(mBinding.inputEt.getText().length());
    switchInput();
  }

  public void setInputText(String msgContent) {
    mBinding.inputEt.setText(msgContent);
    mBinding.inputEt.requestFocus();
    mBinding.inputEt.setSelection(mBinding.inputEt.getText().length());
    switchInput();
  }

  public void clearReplyMsg() {
    replyMessage = null;
    mBinding.replyLayout.setVisibility(GONE);
  }

  public void setInputProperties(InputProperties properties) {
    this.inputProperties = properties;
    loadConfig();
  }

  private void hideAllInputLayout(boolean immediately) {
    postDelayed(
        () -> {
          updateState(InputState.none);
          KeyboardUtils.hideKeyboard(this);
          long delay = immediately ? 0 : SHOW_DELAY_TIME;
          emojiShow(false, delay);
          morePanelShow(false, delay);
        },
        immediately ? 0 : ViewConfiguration.getDoubleTapTimeout());
  }

  @Override
  public void onRecordReady() {
    ALog.d(LIB_TAG, TAG, "onRecordReady");
    ChatMessageAudioControl.getInstance().stopAudio();
  }

  @Override
  public void onRecordStart(File audioFile, RecordType recordType) {
    ALog.d(LIB_TAG, TAG, "onRecordStart");
    startRecord();
  }

  @Override
  public void onRecordSuccess(File audioFile, long audioLength, RecordType recordType) {
    ALog.d(
        LIB_TAG,
        TAG,
        "onRecordSuccess -->> file:" + audioFile.getName() + " length:" + audioLength);
    endRecord();
    mProxy.sendAudio(audioFile, audioLength, replyMessage);
    clearReplyMsg();
  }

  @Override
  public void onRecordFail() {
    ALog.d(LIB_TAG, TAG, "onRecordFail");
    endRecord();
  }

  @Override
  public void onRecordCancel() {
    ALog.d(LIB_TAG, TAG, "onRecordCancel");
    endRecord();
  }

  @Override
  public void onRecordReachedMaxTime(int maxTime) {
    ALog.d(LIB_TAG, TAG, "onRecordReachedMaxTime -->> " + maxTime);
    dismissAudioInputDialog(false);
    mAudioRecorder.handleEndRecord(true, maxTime);
  }

  private void startRecord() {}

  private void endRecord() {}

  private void onKeyboardShow() {
    ALog.d(LIB_TAG, TAG, "onKeyboardShow inputState:" + mInputState);
    if (mInputState != InputState.input) {
      hideCurrentInput();
      updateState(InputState.input);
    }
  }

  private void onKeyboardHide() {
    ALog.d(LIB_TAG, TAG, "onKeyboardHide inputState:" + mInputState);
    if (mInputState == InputState.input) {
      updateState(InputState.none);
    }
  }

  private void hideKeyboard() {
    KeyboardUtils.hideKeyboard(mBinding.inputEt);
    mBinding.inputEt.clearFocus();
  }

  private void showKeyboard() {
    mBinding.inputEt.requestFocus();
    mBinding.inputEt.setSelection(mBinding.inputEt.getText().length());
    KeyboardUtils.showKeyboard(mBinding.inputEt);
  }

  @Override
  public void onTextAdd(String content, int start, int length, boolean hasAt) {
    if (mInputState != InputState.input) {
      postDelayed(this::switchInput, SHOW_DELAY_TIME);
    }
    SpannableString spannable = MessageHelper.generateAtSpanString(hasAt ? content : "@" + content);
    mBinding.inputEt.getEditableText().replace(hasAt ? start : start - 1, start, spannable);
  }

  @Override
  public void onTextDelete(int start, int length) {
    if (mInputState != InputState.input) {
      postDelayed(this::switchInput, SHOW_DELAY_TIME);
    }
    int end = start + length - 1;
    mBinding.inputEt.getEditableText().replace(start, end, "");
  }

  public void loadConfig() {
    if (this.inputProperties != null) {

      if (inputProperties.inputBarBg != null) {
        mBinding.chatMessageInputRoot.setBackground(inputProperties.inputBarBg);
      }

      if (inputProperties.inputEditBg != null) {
        mBinding.inputLayout.setBackground(inputProperties.inputEditBg);
      }

      if (inputProperties.inputMoreBg != null) {
        mBinding.inputMoreLayout.setBackground(inputProperties.inputMoreBg);
      }

      if (inputProperties.inputReplyBg != null) {
        mBinding.replyLayout.setBackground(inputProperties.inputReplyBg);
      }

      if (inputProperties.inputReplyTextColor != null) {
        mBinding.replyContentTv.setTextColor(inputProperties.inputReplyTextColor);
      }

      if (inputProperties.inputEditTextColor != null) {
        mBinding.inputEt.setTextColor(inputProperties.inputEditTextColor);
      }

      if (inputProperties.inputEditHintTextColor != null) {
        mBinding.inputEt.setHintTextColor(inputProperties.inputEditHintTextColor);
      }
    }
  }
}
