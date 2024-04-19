// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.chatkit.ui.fun.view.message.viewholder;

import android.content.Context;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.netease.nimlib.sdk.v2.message.V2NIMMessageRefer;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.model.IMMessageInfo;
import com.netease.yunxin.kit.chatkit.ui.ChatMessageType;
import com.netease.yunxin.kit.chatkit.ui.R;
import com.netease.yunxin.kit.chatkit.ui.common.MessageHelper;
import com.netease.yunxin.kit.chatkit.ui.custom.MultiForwardAttachment;
import com.netease.yunxin.kit.chatkit.ui.databinding.ChatBaseMessageViewHolderBinding;
import com.netease.yunxin.kit.chatkit.ui.databinding.FunChatMessageReplayViewBinding;
import com.netease.yunxin.kit.chatkit.ui.databinding.FunChatMessageRevokedViewBinding;
import com.netease.yunxin.kit.chatkit.ui.model.ChatMessageBean;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.ChatBaseMessageViewHolder;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.ChatMessageViewHolderUIOptions;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.CommonUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.MessageStatusUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.ReplayUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.RevokeUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.SignalUIOption;
import com.netease.yunxin.kit.common.ui.utils.ToastX;
import com.netease.yunxin.kit.common.utils.NetworkUtils;
import com.netease.yunxin.kit.common.utils.SizeUtils;
import com.netease.yunxin.kit.corekit.im2.extend.FetchCallback;
import com.netease.yunxin.kit.corekit.im2.utils.RouterConstant;
import com.netease.yunxin.kit.corekit.route.XKitRouter;

public class FunChatBaseMessageViewHolder extends ChatBaseMessageViewHolder {
  private static final String TAG = "FunChatBaseMessageViewHolder";

  protected FunChatMessageReplayViewBinding replayBinding;
  protected FunChatMessageRevokedViewBinding revokedViewBinding;

  public FunChatBaseMessageViewHolder(
      @NonNull ChatBaseMessageViewHolderBinding parent, int viewType) {
    super(parent, viewType);
  }

  @Override
  protected void onLayoutConfig(ChatMessageBean messageBean) {
    super.onLayoutConfig(messageBean);
    if (messageBean.isRevoked()) {
      // 撤回消息控制居中
      ConstraintLayout.LayoutParams messageContainerLayoutParams =
          (ConstraintLayout.LayoutParams) baseViewBinding.messageContainer.getLayoutParams();
      messageContainerLayoutParams.horizontalBias =
          CommonUIOption.MessageContentLayoutGravity.center;
      baseViewBinding.messageContainer.setLayoutParams(messageContainerLayoutParams);
      ConstraintLayout.LayoutParams messageBottomLayoutParams =
          (ConstraintLayout.LayoutParams) baseViewBinding.messageBottomGroup.getLayoutParams();
      messageBottomLayoutParams.horizontalBias = CommonUIOption.MessageContentLayoutGravity.center;
      baseViewBinding.messageBottomGroup.setLayoutParams(messageBottomLayoutParams);
    }
    // 设置标记提示 margin
    ConstraintLayout.LayoutParams signalLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.llSignal.getLayoutParams();
    int paddingSize = SizeUtils.dp2px(12);
    signalLayoutParams.rightMargin = paddingSize;
    signalLayoutParams.leftMargin = paddingSize;
    baseViewBinding.llSignal.setLayoutParams(signalLayoutParams);
  }

  @Override
  protected void setUserInfo(ChatMessageBean message) {
    // 修改用户头像
    int avatarSize = SizeUtils.dp2px(42);
    int cornerRadius = SizeUtils.dp2px(4);
    ViewGroup.LayoutParams myAvatarLayoutParams = baseViewBinding.myAvatar.getLayoutParams();
    myAvatarLayoutParams.width = avatarSize;
    myAvatarLayoutParams.height = avatarSize;
    baseViewBinding.myAvatar.setCornerRadius(cornerRadius);

    ViewGroup.LayoutParams otherUserAvatarLayoutParams =
        baseViewBinding.otherUserAvatar.getLayoutParams();
    otherUserAvatarLayoutParams.width = avatarSize;
    otherUserAvatarLayoutParams.height = avatarSize;
    baseViewBinding.otherUserAvatar.setCornerRadius(cornerRadius);
    if (message.isRevoked()) {
      if (MessageHelper.isReceivedMessage(message) && revokedViewBinding != null) {
        Context context = revokedViewBinding.messageText.getContext();
        revokedViewBinding.messageText.setText(
            context.getString(
                R.string.fun_chat_message_revoked,
                MessageHelper.getChatMessageUserNameByAccount(message.getSenderId())));
      }
      return;
    }
    super.setUserInfo(message);
  }

  @Override
  protected void onCommonViewVisibleConfig(ChatMessageBean messageBean) {
    super.onCommonViewVisibleConfig(messageBean);
    if (messageBean.isRevoked()) {
      // 消息撤回，头像、名称不展示
      baseViewBinding.myAvatar.setVisibility(View.GONE);
      baseViewBinding.myName.setVisibility(View.GONE);
      baseViewBinding.otherUserAvatar.setVisibility(View.GONE);
      baseViewBinding.otherUsername.setVisibility(View.GONE);
    }
    baseViewBinding.chatSelectorCb.setBackgroundResource(R.drawable.fun_chat_radio_button_selector);
  }

  @Override
  protected ChatMessageViewHolderUIOptions provideUIOptions(ChatMessageBean messageBean) {
    SignalUIOption signalUIOption = new SignalUIOption();
    signalUIOption.signalBgRes = R.color.fun_chat_message_pin_bg_color;
    return ChatMessageViewHolderUIOptions.wrapExitsOptions(super.provideUIOptions(messageBean))
        .signalUIOption(signalUIOption)
        .build();
  }

  @Override
  protected void onMessageBackgroundConfig(ChatMessageBean messageBean) {
    super.onMessageBackgroundConfig(messageBean);
    if (messageBean.isRevoked()) {
      baseViewBinding.messageContainer.setBackgroundResource(R.color.title_transfer);
      return;
    }
    boolean isReceivedMsg = MessageHelper.isReceivedMessage(messageBean) || isForwardMsg();
    CommonUIOption commonUIOption = uiOptions.commonUIOption;
    boolean isCustomBgValid = true;
    if (isReceivedMsg) {
      if (commonUIOption.otherUserMessageBg != null) {
        baseViewBinding.contentWithTopLayer.setBackground(commonUIOption.otherUserMessageBg);
      } else if (commonUIOption.otherUserMessageBgRes != null) {
        baseViewBinding.contentWithTopLayer.setBackgroundResource(
            commonUIOption.otherUserMessageBgRes);
      } else if (properties.getReceiveMessageBg() != null) {
        baseViewBinding.contentWithTopLayer.setBackground(properties.getReceiveMessageBg());
      } else if (properties.receiveMessageRes != null) {
        baseViewBinding.contentWithTopLayer.setBackgroundResource(properties.receiveMessageRes);
      } else {
        isCustomBgValid = false;
      }
    } else {
      if (commonUIOption.myMessageBg != null) {
        baseViewBinding.contentWithTopLayer.setBackground(commonUIOption.myMessageBg);
      } else if (commonUIOption.myMessageBgRes != null) {
        baseViewBinding.contentWithTopLayer.setBackgroundResource(commonUIOption.myMessageBgRes);
      } else if (properties.getSelfMessageBg() != null) {
        baseViewBinding.contentWithTopLayer.setBackground(properties.getSelfMessageBg());
      } else if (properties.selfMessageRes != null) {
        baseViewBinding.contentWithTopLayer.setBackgroundResource(properties.selfMessageRes);
      } else {
        isCustomBgValid = false;
      }
    }
    if (isCustomBgValid) {
      return;
    }
    if (baseViewBinding.messageContainer.getChildCount() <= 0) {
      return;
    }
    View firstChild = baseViewBinding.messageContainer.getChildAt(0);
    if (type == ChatMessageType.LOCATION_MESSAGE_VIEW_TYPE) {
      firstChild.setBackgroundResource(R.drawable.fun_message_location_bg);
      return;
    }

    if (isReceivedMsg) {
      firstChild.setBackgroundResource(R.drawable.fun_message_receive_bg);
    } else {
      if (messageBean.getMessageData().getAttachment() instanceof MultiForwardAttachment) {
        firstChild.setBackgroundResource(R.drawable.fun_forward_message_send_bg);
      } else {
        firstChild.setBackgroundResource(R.drawable.fun_message_send_bg);
      }
    }
  }

  @Override
  protected void onMessageRevoked(ChatMessageBean messageBean) {
    RevokeUIOption revokeUIOption = uiOptions.revokeUIOption;
    if (revokeUIOption.enable != null && !revokeUIOption.enable) {
      return;
    }
    if (!messageBean.isRevoked()) {
      baseViewBinding.messageContainer.setEnabled(true);
      return;
    }
    baseViewBinding.messageContainer.setEnabled(false);
    baseViewBinding.messageTopGroup.removeAllViews();
    baseViewBinding.messageContainer.removeAllViews();
    baseViewBinding.messageBottomGroup.removeAllViews();
    addRevokeViewToMessageContainer();
    Context context = revokedViewBinding.messageText.getContext();
    if (MessageHelper.isReceivedMessage(messageBean)) {
      revokedViewBinding.messageText.setText(
          context.getString(
              R.string.fun_chat_message_revoked,
              MessageHelper.getChatMessageUserNameByAccount(messageBean.getSenderId())));
    } else {
      revokedViewBinding.messageText.setText(
          context.getString(
              R.string.fun_chat_message_revoked, context.getString(R.string.chat_you)));
    }
    // reedit
    revokedViewBinding.tvAction.setOnClickListener(
        v -> {
          if (itemClickListener != null && !isMultiSelect) {
            itemClickListener.onReEditRevokeMessage(v, position, messageBean);
          }
        });
    if (MessageHelper.revokeMsgIsEdit(messageBean)) {
      revokedViewBinding.tvAction.setVisibility(View.VISIBLE);
    } else {
      revokedViewBinding.tvAction.setVisibility(View.GONE);
    }
    if (revokeUIOption.revokedTipText != null) {
      revokedViewBinding.messageText.setText(revokeUIOption.revokedTipText);
    }
    if (revokeUIOption.actionBtnText != null) {
      revokedViewBinding.tvAction.setText(revokeUIOption.actionBtnText);
    }
    if (revokeUIOption.actionBtnVisible != null) {
      revokedViewBinding.tvAction.setVisibility(
          revokeUIOption.actionBtnVisible ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  protected void setStatus(ChatMessageBean data) {
    baseViewBinding.readProcess.setColor(
        parent.getContext().getResources().getColor(R.color.color_58be6b));
    super.setStatus(data);
    MessageStatusUIOption messageStatusUIOption = uiOptions.messageStatusUIOption;
    if (messageStatusUIOption.readProcessClickListener != null) {
      baseViewBinding.readProcess.setOnClickListener(
          v -> {
            if (!isMultiSelect) {
              messageStatusUIOption.readProcessClickListener.onClick(v);
            }
          });
    } else {
      baseViewBinding.readProcess.setOnClickListener(
          v -> {
            if (!isMultiSelect) {
              if (!NetworkUtils.isConnected()) {
                ToastX.showShortToast(R.string.chat_network_error_tip);
                return;
              }
              XKitRouter.withKey(RouterConstant.PATH_FUN_CHAT_READER_PAGE)
                  .withParam(RouterConstant.KEY_MESSAGE, data.getMessageData().getMessage())
                  .withContext(v.getContext())
                  .navigate();
            }
          });
    }
  }

  @Override
  protected void setReplyInfo(ChatMessageBean messageBean) {
    replyMessage = null;
    ReplayUIOption replayUIOption = uiOptions.replayUIOption;
    if (replayUIOption.enable != null && !replayUIOption.enable) {
      return;
    }
    if (messageBean == null || messageBean.getMessageData() == null) {
      return;
    }
    baseViewBinding.messageBottomGroup.removeAllViews();
    ALog.w(
        TAG,
        TAG,
        "setReplyInfo, uuid=" + messageBean.getMessageData().getMessage().getMessageClientId());
    if (messageBean.hasReply()) {
      // 自定义回复实现
      addReplayViewToBottomGroup();
      String replyUuid = messageBean.getReplyUUid();
      if (!TextUtils.isEmpty(replyUuid)) {
        MessageHelper.getReplyMessageInfo(
            replyUuid,
            new FetchCallback<>() {
              @Override
              public void onError(int errorCode, @Nullable String errorMsg) {
                baseViewBinding.messageTopGroup.removeAllViews();
              }

              @Override
              public void onSuccess(@Nullable IMMessageInfo param) {
                replyMessage = param;
                String content = MessageHelper.getReplyContent(replyMessage);
                MessageHelper.identifyFaceExpression(
                    replayBinding.tvReply.getContext(),
                    replayBinding.tvReply,
                    content,
                    ImageSpan.ALIGN_BOTTOM);
              }
            });
      }

      if (itemClickListener != null) {
        replayBinding.tvReply.setOnClickListener(
            v -> {
              if (!isMultiSelect) {
                itemClickListener.onReplyMessageClick(v, position, replyMessage);
              }
            });
      }
    } else if (MessageHelper.isThreadReplayInfo(messageBean)) {
      // thread 回复
      setThreadReplyInfo(messageBean);
    } else {
      baseViewBinding.messageTopGroup.removeAllViews();
    }
  }

  /// 内部设置 thread 回复消息
  private void setThreadReplyInfo(ChatMessageBean messageBean) {
    V2NIMMessageRefer threadOption = messageBean.getMessageData().getMessage().getThreadReply();
    String replyFrom = threadOption.getSenderId();
    if (TextUtils.isEmpty(replyFrom)) {
      ALog.w(
          TAG,
          "no reply message found, uuid="
              + messageBean.getMessageData().getMessage().getMessageClientId());
      baseViewBinding.messageTopGroup.removeAllViews();
      return;
    }
    addReplayViewToBottomGroup();
    String replyUuid = threadOption.getMessageClientId();
    MessageHelper.getReplyMessageInfo(
        replyUuid,
        new FetchCallback<>() {

          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            replayBinding.tvReply.setVisibility(View.GONE);
          }

          @Override
          public void onSuccess(@Nullable IMMessageInfo param) {
            if (param != null) {
              replyMessage = param;
              String content = MessageHelper.getReplyContent(replyMessage);
              MessageHelper.identifyFaceExpression(
                  replayBinding.tvReply.getContext(),
                  replayBinding.tvReply,
                  content,
                  ImageSpan.ALIGN_BOTTOM);
            }
          }
        });

    if (itemClickListener != null) {
      replayBinding.tvReply.setOnClickListener(
          v -> {
            if (!isMultiSelect) {
              itemClickListener.onReplyMessageClick(v, position, replyMessage);
            }
          });
    }
  }

  // 添加 normal 下的回复布局
  private void addReplayViewToBottomGroup() {
    replayBinding =
        FunChatMessageReplayViewBinding.inflate(
            LayoutInflater.from(parent.getContext()), baseViewBinding.messageBottomGroup, true);
  }

  private void addRevokeViewToMessageContainer() {
    revokedViewBinding =
        FunChatMessageRevokedViewBinding.inflate(
            LayoutInflater.from(parent.getContext()), baseViewBinding.messageContainer, true);
  }
}
