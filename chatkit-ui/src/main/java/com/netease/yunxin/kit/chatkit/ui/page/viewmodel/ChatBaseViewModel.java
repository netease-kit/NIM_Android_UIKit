// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.chatkit.ui.page.viewmodel;

import static com.netease.yunxin.kit.chatkit.ui.ChatKitUIConstant.LIB_TAG;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.netease.nimlib.sdk.v2.conversation.enums.V2NIMConversationType;
import com.netease.nimlib.sdk.v2.message.V2NIMClearHistoryNotification;
import com.netease.nimlib.sdk.v2.message.V2NIMCollection;
import com.netease.nimlib.sdk.v2.message.V2NIMMessage;
import com.netease.nimlib.sdk.v2.message.V2NIMMessageCreator;
import com.netease.nimlib.sdk.v2.message.V2NIMMessageDeletedNotification;
import com.netease.nimlib.sdk.v2.message.V2NIMMessagePin;
import com.netease.nimlib.sdk.v2.message.V2NIMMessagePinNotification;
import com.netease.nimlib.sdk.v2.message.V2NIMMessageQuickCommentNotification;
import com.netease.nimlib.sdk.v2.message.V2NIMMessageRefer;
import com.netease.nimlib.sdk.v2.message.V2NIMP2PMessageReadReceipt;
import com.netease.nimlib.sdk.v2.message.V2NIMTeamMessageReadReceipt;
import com.netease.nimlib.sdk.v2.message.config.V2NIMMessageConfig;
import com.netease.nimlib.sdk.v2.message.config.V2NIMMessagePushConfig;
import com.netease.nimlib.sdk.v2.message.enums.V2NIMMessagePinState;
import com.netease.nimlib.sdk.v2.message.enums.V2NIMMessageQueryDirection;
import com.netease.nimlib.sdk.v2.message.enums.V2NIMMessageSendingState;
import com.netease.nimlib.sdk.v2.message.enums.V2NIMMessageType;
import com.netease.nimlib.sdk.v2.message.option.V2NIMMessageListOption;
import com.netease.nimlib.sdk.v2.message.params.V2NIMSendMessageParams;
import com.netease.nimlib.sdk.v2.message.result.V2NIMSendMessageResult;
import com.netease.nimlib.sdk.v2.utils.V2NIMConversationIdUtil;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.cache.FriendUserCache;
import com.netease.yunxin.kit.chatkit.map.ChatLocationBean;
import com.netease.yunxin.kit.chatkit.media.ImageUtil;
import com.netease.yunxin.kit.chatkit.model.IMMessageInfo;
import com.netease.yunxin.kit.chatkit.observer.MessageListener;
import com.netease.yunxin.kit.chatkit.observer.MessageRevokeNotification;
import com.netease.yunxin.kit.chatkit.observer.MessageUpdateType;
import com.netease.yunxin.kit.chatkit.repo.ChatRepo;
import com.netease.yunxin.kit.chatkit.repo.ContactRepo;
import com.netease.yunxin.kit.chatkit.repo.MessageSendingObserver;
import com.netease.yunxin.kit.chatkit.repo.ResourceRepo;
import com.netease.yunxin.kit.chatkit.repo.SettingRepo;
import com.netease.yunxin.kit.chatkit.ui.ChatKitUIConstant;
import com.netease.yunxin.kit.chatkit.ui.R;
import com.netease.yunxin.kit.chatkit.ui.common.ChatUserCache;
import com.netease.yunxin.kit.chatkit.ui.common.ChatUtils;
import com.netease.yunxin.kit.chatkit.ui.common.MessageHelper;
import com.netease.yunxin.kit.chatkit.ui.common.V2ChatCallback;
import com.netease.yunxin.kit.chatkit.ui.custom.MultiForwardAttachment;
import com.netease.yunxin.kit.chatkit.ui.model.AnchorScrollInfo;
import com.netease.yunxin.kit.chatkit.ui.model.ChatMessageBean;
import com.netease.yunxin.kit.chatkit.ui.model.MessageRevokeInfo;
import com.netease.yunxin.kit.chatkit.ui.view.ait.AitService;
import com.netease.yunxin.kit.chatkit.utils.SendMediaHelper;
import com.netease.yunxin.kit.common.ui.utils.ToastX;
import com.netease.yunxin.kit.common.ui.viewmodel.BaseViewModel;
import com.netease.yunxin.kit.common.ui.viewmodel.FetchResult;
import com.netease.yunxin.kit.common.ui.viewmodel.LoadStatus;
import com.netease.yunxin.kit.common.utils.EncryptUtils;
import com.netease.yunxin.kit.common.utils.FileUtils;
import com.netease.yunxin.kit.common.utils.ImageUtils;
import com.netease.yunxin.kit.common.utils.UriUtils;
import com.netease.yunxin.kit.corekit.im2.IMKitClient;
import com.netease.yunxin.kit.corekit.im2.extend.FetchCallback;
import com.netease.yunxin.kit.corekit.im2.extend.ProgressFetchCallback;
import com.netease.yunxin.kit.corekit.im2.listener.ContactListener;
import com.netease.yunxin.kit.corekit.im2.listener.V2FriendChangeType;
import com.netease.yunxin.kit.corekit.im2.listener.V2UserListener;
import com.netease.yunxin.kit.corekit.im2.model.FriendAddApplicationInfo;
import com.netease.yunxin.kit.corekit.im2.model.IMMessageProgress;
import com.netease.yunxin.kit.corekit.im2.model.UserWithFriend;
import com.netease.yunxin.kit.corekit.im2.model.V2UserInfo;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

/** 消息ViewModel 基类 消息接受、发送、撤回等逻辑 用户、好友信息变更监听等 */
public abstract class ChatBaseViewModel extends BaseViewModel {
  public static final String TAG = "ChatViewModel";

  // 撤回消息超时时间
  private static final int RES_REVOKE_TIMEOUT = 107314;

  //自己手动撤回的消息的messageClientId，
  //用于判断是否是自己撤回的消息
  // 自己撤回的消息，收到通知后不再做处理
  private String revokedMessageClientId;

  // 拉取历史消息
  private final MutableLiveData<FetchResult<List<ChatMessageBean>>> messageLiveData =
      new MutableLiveData<>();
  private final FetchResult<List<ChatMessageBean>> messageFetchResult =
      new FetchResult<>(LoadStatus.Finish);
  // 接受消息
  private final MutableLiveData<FetchResult<List<ChatMessageBean>>> messageRecLiveData =
      new MutableLiveData<>();

  // 用户信息变更，只有点击到用户个人信息页面才会远端拉取，如果有变更本地同步刷新
  private final MutableLiveData<FetchResult<List<String>>> userChangeLiveData =
      new MutableLiveData<>();

  // 标记消息LiveData
  private final MutableLiveData<FetchResult<Map<String, V2NIMMessagePin>>> msgPinLiveData =
      new MutableLiveData<>();
  // 消息发送LiveData，本地发送消息通过该LiveData通知UI
  private final MutableLiveData<FetchResult<ChatMessageBean>> sendMessageLiveData =
      new MutableLiveData<>();
  private final FetchResult<ChatMessageBean> sendMessageFetchResult =
      new FetchResult<>(LoadStatus.Finish);
  // 消息附件下载进度LiveData
  private final MutableLiveData<FetchResult<IMMessageProgress>> attachmentProgressMutableLiveData =
      new MutableLiveData<>();
  // 撤回消息LiveData
  private final MutableLiveData<FetchResult<List<MessageRevokeInfo>>> revokeMessageLiveData =
      new MutableLiveData<>();
  // 删除消息LiveData
  private final MutableLiveData<FetchResult<List<V2NIMMessageRefer>>> deleteMessageLiveData =
      new MutableLiveData<>();

  private final MutableLiveData<FetchResult<Pair<MessageUpdateType, List<ChatMessageBean>>>>
      updateMessageLiveData = new MutableLiveData<>();

  // 当前会话账号ID，单聊则为对方账号，群聊则为群ID
  protected String mChatAccountId;
  // 当前会话ID
  protected String mConversationId;
  // 当前会话类型
  private V2NIMConversationType mSessionType;
  // 是否群聊
  protected boolean mIsTeamGroup = false;
  // 是否需要消息回执
  protected boolean needACK = false;
  // 是否显示已读状态
  protected boolean showRead = true;
  // 是否有加载更多消息
  protected boolean hasLoadMessage = false;

  // 消息分页大小
  private final int messagePageSize = 100;
  // 视频图片旋转角度，适配部分机型发送图片旋转问题
  private final String Orientation_Vertical = "90";

  // 消息监听
  private final MessageListener messageListener =
      new MessageListener() {

        // 消息附件下载进度
        @Override
        public void onMessageAttachmentDownloadProgress(
            @NonNull V2NIMMessage message, int progress) {
          if (message.getConversationId().equals(mConversationId)) {
            ALog.d(LIB_TAG, TAG, "onMessageAttachmentDownloadProgress -->> " + progress);
            FetchResult<IMMessageProgress> result = new FetchResult<>(LoadStatus.Success);
            result.setData(new IMMessageProgress(message.getMessageClientId(), progress));
            result.setType(FetchResult.FetchType.Update);
            result.setTypeIndex(-1);
            attachmentProgressMutableLiveData.setValue(result);
          }
        }

        // 消息发送状态变更
        @Override
        public void onMessagesUpdate(
            @NonNull List<IMMessageInfo> messages, @NonNull MessageUpdateType type) {
          if (messages.isEmpty()) {
            return;
          }
          IMMessageInfo firstMessage = messages.get(0);
          if (!firstMessage.getMessage().getConversationId().equals(mConversationId)) {
            return;
          }
          ALog.d(LIB_TAG, TAG, "onMessagesUpdate -->> " + messages.size() + ", type:" + type);
          FetchResult<Pair<MessageUpdateType, List<ChatMessageBean>>> messageUpdateResult =
              new FetchResult<>(LoadStatus.Success);
          messageUpdateResult.setData(new Pair<>(type, convert(messages)));
          messageUpdateResult.setType(FetchResult.FetchType.Update);
          messageUpdateResult.setTypeIndex(-1);
          updateMessageLiveData.setValue(messageUpdateResult);
        }

        // 接收到新消息
        @Override
        public void onReceiveMessages(@NonNull List<IMMessageInfo> messages) {
          IMMessageInfo firstMessage = messages.get(0);
          if (!firstMessage.getMessage().getConversationId().equals(mConversationId)) {
            return;
          }
          ALog.d(LIB_TAG, TAG, "receive msg -->> " + messages.size());
          FetchResult<List<ChatMessageBean>> messageRecFetchResult =
              new FetchResult<>(LoadStatus.Success);
          messageRecFetchResult.setData(convert(messages));
          messageRecFetchResult.setType(FetchResult.FetchType.Add);
          messageRecFetchResult.setTypeIndex(-1);
          messageRecLiveData.setValue(messageRecFetchResult);
        }

        @Override
        public void onClearHistoryNotifications(
            @Nullable List<? extends V2NIMClearHistoryNotification> clearHistoryNotifications) {}

        @Override
        public void onMessageDeletedNotifications(
            @Nullable List<? extends V2NIMMessageDeletedNotification> messages) {
          ALog.d(
              LIB_TAG,
              TAG,
              "msg delete batch -->> " + (messages == null ? "null" : messages.size()));
          if (messages != null) {
            ArrayList<V2NIMMessageRefer> deleteList = new ArrayList<>();
            FetchResult<List<V2NIMMessageRefer>> result = new FetchResult<>(LoadStatus.Success);
            for (V2NIMMessageDeletedNotification msg : messages) {
              if (TextUtils.equals(msg.getMessageRefer().getConversationId(), mConversationId)) {
                deleteList.add(msg.getMessageRefer());
              }
            }
            if (deleteList.size() > 0) {
              result.setData(deleteList);
              result.setType(FetchResult.FetchType.Remove);
              result.setTypeIndex(-1);
              deleteMessageLiveData.setValue(result);
            }
          }
        }

        @Override
        public void onMessageQuickCommentNotification(
            @Nullable V2NIMMessageQuickCommentNotification quickCommentNotification) {}

        @Override
        public void onMessagePinNotification(
            @Nullable V2NIMMessagePinNotification pinNotification) {
          ALog.d(LIB_TAG, TAG, "onMessagePinNotification");
          if (pinNotification != null
              && Objects.equals(
                  pinNotification.getPin().getMessageRefer().getConversationId(),
                  mConversationId)) {
            ALog.d(
                LIB_TAG, TAG, "onMessagePinNotification:" + pinNotification.getPinState().name());
            if (pinNotification.getPinState()
                == V2NIMMessagePinState.V2NIM_MESSAGE_PIN_STEATE_PINNED) {
              Pair<String, V2NIMMessagePin> pinInfo =
                  new Pair<>(
                      pinNotification.getPin().getMessageRefer().getMessageClientId(),
                      pinNotification.getPin());
              addPinMessageLiveData.setValue(pinInfo);
            } else if (pinNotification.getPinState()
                == V2NIMMessagePinState.V2NIM_MESSAGE_PIN_STEATE_NOT_PINNED) {
              removePinMessageLiveData.setValue(
                  pinNotification.getPin().getMessageRefer().getMessageClientId());
            }
          }
        }

        @Override
        public void onMessageRevokeNotifications(
            @Nullable List<MessageRevokeNotification> revokeNotifications) {
          if (revokeNotifications == null) {
            return;
          }
          FetchResult<List<MessageRevokeInfo>> result = new FetchResult<>(LoadStatus.Success);
          List<MessageRevokeInfo> revokedList = new ArrayList<>();
          for (MessageRevokeNotification revokeNotification : revokeNotifications) {
            //判断不是自己撤回的，并且是当前会话的消息
            if (!TextUtils.equals(
                    revokedMessageClientId,
                    revokeNotification.getNimNotification().getMessageRefer().getMessageClientId())
                && TextUtils.equals(
                    revokeNotification.getNimNotification().getMessageRefer().getConversationId(),
                    mConversationId)) {
              revokedList.add(new MessageRevokeInfo(null, revokeNotification.getNimNotification()));
            }
          }
          result.setData(revokedList);
          result.setType(FetchResult.FetchType.Remove);
          result.setTypeIndex(-1);
          revokeMessageLiveData.setValue(result);
        }

        @Override
        public void onReceiveTeamMessageReadReceipts(
            @Nullable List<? extends V2NIMTeamMessageReadReceipt> readReceipts) {
          if (readReceipts == null) {
            return;
          }
          List<V2NIMTeamMessageReadReceipt> teamReceipts = new ArrayList<>(readReceipts);
          onTeamMessageReadReceipts(teamReceipts);
        }

        @Override
        public void onReceiveP2PMessageReadReceipts(
            @Nullable List<? extends V2NIMP2PMessageReadReceipt> readReceipts) {
          if (readReceipts == null) {
            return;
          }
          List<V2NIMP2PMessageReadReceipt> p2pReceipts = new ArrayList<>(readReceipts);
          onP2PMessageReadReceipts(p2pReceipts);
        }
      };

  //群消息已读回执处理
  protected void onTeamMessageReadReceipts(List<V2NIMTeamMessageReadReceipt> readReceipts) {}

  //点对点消息处理已读回执
  protected void onP2PMessageReadReceipts(List<V2NIMP2PMessageReadReceipt> readReceipts) {}

  // 消息发送监听，正在发送中的消息。Kit层逻辑，只要调用ChatRepo发送消息，都会在这里回调，方便页面消息加载
  private final MessageSendingObserver sendingObserver =
      new MessageSendingObserver() {
        @Override
        public void onSuccess(@Nullable V2NIMMessage data) {
          super.onSuccess(data);
          if (data != null && TextUtils.equals(data.getConversationId(), mConversationId)) {
            ALog.d(LIB_TAG, TAG, "sendingObserver onSuccess -->> " + mConversationId);
            postMessageSend(new IMMessageInfo(data), false);
          }
        }

        @Override
        public void onError(
            int errorCode,
            @NonNull String errorMsg,
            @NonNull String conversationId,
            @NonNull V2NIMConversationType conversationType,
            @Nullable V2NIMMessage data) {
          super.onError(errorCode, errorMsg, conversationId, conversationType, data);
        }

        @Override
        public void onProgress(
            int progress,
            @NonNull String conversationId,
            @NonNull V2NIMConversationType conversationType,
            @Nullable V2NIMMessage data) {
          super.onProgress(progress, conversationId, conversationType, data);
          ALog.d(
              LIB_TAG,
              TAG,
              "sendingObserver onProgress update -->> "
                  + conversationId
                  + ",,"
                  + progress
                  + ","
                  + TextUtils.equals(conversationId, mConversationId)
                  + "data:"
                  + (data != null));
          if (TextUtils.equals(conversationId, mConversationId) && data != null) {
            //发送状态回调，代替之前的附件上传进度
            if (progress == 0) {
              postMessageSend(new IMMessageInfo(data), true);
            }
            FetchResult<IMMessageProgress> result = new FetchResult<>(LoadStatus.Success);
            result.setData(new IMMessageProgress(data.getMessageClientId(), progress));
            result.setType(FetchResult.FetchType.Update);
            result.setTypeIndex(-1);
            attachmentProgressMutableLiveData.setValue(result);
          }
        }
      };

  // 好友信息变更监听
  private final ContactListener friendListener =
      new ContactListener() {
        @Override
        public void onFriendChange(
            @NonNull V2FriendChangeType friendChangeType,
            @NonNull List<? extends UserWithFriend> friendList) {

          List<UserWithFriend> needFriendList = new ArrayList<>();
          List<String> accountList = new ArrayList<>();
          for (UserWithFriend friendInfo : friendList) {
            if (friendInfo != null && FriendUserCache.isFriend(friendInfo.getAccount())) {
              needFriendList.add(friendInfo);
              accountList.add(friendInfo.getAccount());
            }
          }

          if (needFriendList.size() > 0) {
            FetchResult<List<String>> userInfoFetchResult = new FetchResult<>(LoadStatus.Finish);
            userInfoFetchResult.setData(accountList);
            userInfoFetchResult.setType(FetchResult.FetchType.Update);
            userChangeLiveData.setValue(userInfoFetchResult);
          }
          if (friendChangeType == V2FriendChangeType.Update) {
            for (UserWithFriend friend : friendList) {
              if (friend.getAccount().equals(mChatAccountId)) {
                notifyFriendChange(friend);
              }
            }
          }
        }

        @Override
        public void onFriendAddApplication(@NonNull FriendAddApplicationInfo friendApplication) {}

        @Override
        public void onFriendAddRejected(@NonNull FriendAddApplicationInfo rejectionInfo) {}
      };

  // 用户信息变更，非好友关系SDK不会主动回调，只有进入当当前用户的个人信息页面，会主动调用远端拉取。
  private final V2UserListener userListener =
      users -> {
        if (users.isEmpty()) {
          return;
        }
        List<V2UserInfo> needCache = new ArrayList<>();
        List<String> needCacheAccount = new ArrayList<>();
        for (V2UserInfo user : users) {
          if (ChatUserCache.getInstance().containsUser(user.getAccountId())) {
            needCache.add(user);
            needCacheAccount.add(user.getAccountId());
          }
        }

        if (needCache.size() > 0) {
          ChatUserCache.getInstance().addUserInfo(needCache);

          FetchResult<List<String>> userInfoFetchResult = new FetchResult<>(LoadStatus.Finish);
          userInfoFetchResult.setData(needCacheAccount);
          userInfoFetchResult.setType(FetchResult.FetchType.Update);
          userChangeLiveData.setValue(userInfoFetchResult);
        }
      };

  // 获取撤回消息LiveData
  public MutableLiveData<FetchResult<List<MessageRevokeInfo>>> getRevokeMessageLiveData() {
    return revokeMessageLiveData;
  }

  // 获取查询消息LiveData
  public MutableLiveData<FetchResult<List<ChatMessageBean>>> getQueryMessageLiveData() {
    return messageLiveData;
  }
  // 获取接收消息LiveData
  public MutableLiveData<FetchResult<List<ChatMessageBean>>> getRecMessageLiveData() {
    return messageRecLiveData;
  }

  // 获取用户信息变更LiveData
  public MutableLiveData<FetchResult<List<String>>> getUserChangeLiveData() {
    return userChangeLiveData;
  }

  // 获取消息更新LiveData
  public MutableLiveData<FetchResult<Pair<MessageUpdateType, List<ChatMessageBean>>>>
      getUpdateMessageLiveData() {
    return updateMessageLiveData;
  }

  // 获取标记消息LiveData
  public MutableLiveData<FetchResult<Map<String, V2NIMMessagePin>>> getMsgPinLiveData() {
    return msgPinLiveData;
  }

  // 获取删除消息LiveData
  public MutableLiveData<FetchResult<List<V2NIMMessageRefer>>> getDeleteMessageLiveData() {
    return deleteMessageLiveData;
  }

  /**
   * 批量删除消息，一次最多50条
   *
   * @param messageList 消息列表
   */
  public void deleteMessage(List<ChatMessageBean> messageList) {
    if (messageList == null || messageList.isEmpty()) {
      return;
    }
    if (messageList.size() < 2) {
      ChatRepo.deleteMessage(
          messageList.get(0).getMessageData(),
          null,
          false,
          new FetchCallback<>() {
            @Override
            public void onError(int errorCode, @Nullable String errorMsg) {
              FetchResult<List<V2NIMMessageRefer>> fetchResult =
                  new FetchResult<>(LoadStatus.Error);
              fetchResult.setError(-1, R.string.chat_message_delete_error);
              deleteMessageLiveData.setValue(fetchResult);
              ALog.d(LIB_TAG, TAG, "deleteMessage,onFailed:" + errorCode + " errorMsg:" + errorMsg);
            }

            @Override
            public void onSuccess(@Nullable Void data) {
              doActionAfterDelete(messageList);
            }
          });
    } else {
      List<V2NIMMessage> deleteList = new ArrayList<>();
      boolean onlyDeleteLocal = true;
      for (ChatMessageBean messageBean : messageList) {
        deleteList.add(messageBean.getMessageData().getMessage());
        //只要有一条成功的消息就不会只删除本地
        if (!TextUtils.isEmpty(messageBean.getMessageData().getMessage().getMessageServerId())) {
          onlyDeleteLocal = false;
        }
      }
      ChatRepo.deleteMessages(
          deleteList,
          null,
          onlyDeleteLocal,
          new FetchCallback<>() {
            @Override
            public void onError(int errorCode, @Nullable String errorMsg) {
              FetchResult<List<V2NIMMessageRefer>> fetchResult =
                  new FetchResult<>(LoadStatus.Error);
              fetchResult.setError(-1, R.string.chat_message_delete_error);
              deleteMessageLiveData.setValue(fetchResult);
              ALog.d(
                  LIB_TAG, TAG, "deleteMessages,onFailed:" + errorCode + " errorMsg:" + errorMsg);
            }

            @Override
            public void onSuccess(@Nullable Void data) {
              doActionAfterDelete(messageList);
            }
          });
    }
  }
  // 执行删除消息后的操作
  private void doActionAfterDelete(List<ChatMessageBean> messageBean) {
    List<V2NIMMessageRefer> deleteMessageList = new ArrayList<>();
    for (ChatMessageBean message : messageBean) {
      deleteMessageList.add(message.getMessageData().getMessage());
    }
    FetchResult<List<V2NIMMessageRefer>> result = new FetchResult<>(LoadStatus.Success);
    result.setData(deleteMessageList);
    result.setType(FetchResult.FetchType.Remove);
    result.setTypeIndex(-1);
    deleteMessageLiveData.setValue(result);
    ALog.d(LIB_TAG, TAG, "deleteMessage, onSuccess");
  }

  // 好友变更通知，子类根据自己需要重写
  public void notifyFriendChange(UserWithFriend friend) {}
  // 撤回消息
  public void revokeMessage(ChatMessageBean messageBean) {
    if (messageBean != null && messageBean.getMessageData() != null) {
      ALog.d(
          LIB_TAG,
          TAG,
          "revokeMessage " + messageBean.getMessageData().getMessage().getMessageClientId());
      revokedMessageClientId = messageBean.getMessageData().getMessage().getMessageClientId();
      ChatRepo.revokeMessage(
          messageBean.getMessageData(),
          null,
          new FetchCallback<>() {
            @Override
            public void onError(int errorCode, @Nullable String errorMsg) {
              FetchResult<List<MessageRevokeInfo>> fetchResult =
                  new FetchResult<>(LoadStatus.Error);
              fetchResult.setError(
                  errorCode,
                  errorCode == RES_REVOKE_TIMEOUT
                      ? R.string.chat_message_revoke_over_time
                      : R.string.chat_message_revoke_error);
              revokeMessageLiveData.setValue(fetchResult);
              ALog.d(LIB_TAG, TAG, "revokeMessage,onFailed:" + errorCode + " errorMsg:" + errorMsg);
            }

            @Override
            public void onSuccess(@Nullable Void data) {
              if (!TextUtils.isEmpty(messageBean.getPinAccid())) {
                ChatRepo.removeMessagePin(messageBean.getMessageData().getMessage(), null);
              }

              FetchResult<List<MessageRevokeInfo>> fetchResult =
                  new FetchResult<>(LoadStatus.Success);
              MessageRevokeInfo messageRevokeInfo =
                  new MessageRevokeInfo(messageBean.getMessageData().getMessage(), null);
              fetchResult.setData(Collections.singletonList(messageRevokeInfo));
              revokeMessageLiveData.setValue(fetchResult);

              ALog.d(LIB_TAG, TAG, "revokeMessage, onSuccess");
            }
          });
    }
  }

  // 获取发送消息LiveData
  public MutableLiveData<FetchResult<ChatMessageBean>> getSendMessageLiveData() {
    return sendMessageLiveData;
  }

  // 获取消息附件下载进度LiveData
  public MutableLiveData<FetchResult<IMMessageProgress>> getAttachmentProgressMutableLiveData() {
    return attachmentProgressMutableLiveData;
  }

  // 初始化，缓存清理、已读未读开关获取
  public void init(String accountId, V2NIMConversationType sessionType) {
    ALog.d(LIB_TAG, TAG, "init accountId:" + accountId + " sessionType:" + sessionType);
    this.mChatAccountId = accountId;
    this.mConversationId = V2NIMConversationIdUtil.conversationId(accountId, sessionType);
    this.mSessionType = sessionType;
    ChatUserCache.getInstance().clear();
    SettingRepo.getShowReadStatus(
        new FetchCallback<>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {}

          @Override
          public void onSuccess(@Nullable Boolean param) {
            needACK = Boolean.TRUE.equals(param);
          }
        });
  }

  // 设置当前会话账号，清理未读数
  public void setChattingAccount() {
    ALog.d(LIB_TAG, TAG, "setChattingAccount sessionId:" + mConversationId);
    ChatRepo.setChattingId(mConversationId, mSessionType);
    AitService.getInstance().clearAitInfo(mConversationId);
  }

  // 设置是否群聊
  public void setTeamGroup(boolean group) {
    mIsTeamGroup = group;
  }

  // 获取会话ID
  public String getConversationId() {
    return mConversationId;
  }

  // 是否展示已读未读状态
  public void setShowReadStatus(boolean show) {
    showRead = show;
  }

  // 清理
  public void clearChattingAccount() {
    ChatRepo.clearChattingId();
  }

  // 注册监听
  public void addListener() {
    ALog.d(LIB_TAG, TAG, "registerObservers ");

    ChatRepo.addMessageListener(messageListener);
    ContactRepo.addFriendListener(friendListener);
    ContactRepo.addUserListener(userListener);
    ChatRepo.addMessageSendingListener(sendingObserver);
  }

  // 移除监听
  public void removeListener() {
    ALog.d(LIB_TAG, TAG, "unregisterObservers ");
    ChatUserCache.getInstance().clear();
    ChatRepo.removeMessageListener(messageListener);
    ContactRepo.removeFriendListener(friendListener);
    ContactRepo.removeUserListener(userListener);
    ChatRepo.removeMessageSendingListener(sendingObserver);
  }

  // 发送文本消息
  public void sendTextMessage(String content, List<String> pushList) {
    ALog.d(LIB_TAG, TAG, "sendTextMessage:" + (content != null ? content.length() : "null"));
    sendTextMessage(content, pushList, null);
  }

  // 发送文本消息
  public void sendTextMessage(String content, String session, V2NIMConversationType sessionType) {
    ALog.d(LIB_TAG, TAG, "sendTextMessage:" + (content != null ? content.length() : "null"));
    if (content != null) {
      V2NIMMessageConfig config =
          V2NIMMessageConfig.V2NIMMessageConfigBuilder.builder()
              .withReadReceiptEnabled(needACK && showRead)
              .build();
      V2NIMSendMessageParams params =
          V2NIMSendMessageParams.V2NIMSendMessageParamsBuilder.builder()
              .withMessageConfig(config)
              .build();
      ChatRepo.sendTextMessage(session, sessionType, content, params, null);
    }
  }

  // 发送文本消息
  public void sendTextMessage(
      String content, List<String> pushList, Map<String, Object> remoteExtension) {
    ALog.d(LIB_TAG, TAG, "sendTextMessage:" + (content != null ? content.length() : "null"));
    V2NIMMessage textMessage = V2NIMMessageCreator.createTextMessage(content);
    sendMessage(textMessage, pushList, remoteExtension);
  }

  // 添加收藏
  public void addMsgCollection(IMMessageInfo messageInfo) {
    if (messageInfo == null) {
      return;
    }
    ALog.d(LIB_TAG, TAG, "addMsgCollection:" + messageInfo.getMessage().getMessageClientId());
    ChatRepo.collectMessage(
        messageInfo.getMessage(), new V2ChatCallback<V2NIMCollection>().setShowSuccess(true));
  }

  // 发送语音消息
  public void sendAudioMessage(File audio, int audioLength) {
    if (audio != null) {
      ALog.d(LIB_TAG, TAG, "sendAudioMessage:" + audio.getPath());
      V2NIMMessage audioMessage =
          V2NIMMessageCreator.createAudioMessage(
              audio.getPath(), audio.getName(), null, audioLength);
      sendMessage(audioMessage, null, null);
    }
  }

  // 发送图片消息
  public void sendImageMessage(File imageFile) {
    if (imageFile != null) {
      ALog.d(LIB_TAG, TAG, "sendImageMessage:" + imageFile.getPath());
      int[] bounds = ImageUtils.getSize(imageFile);
      V2NIMMessage imageMessage =
          V2NIMMessageCreator.createImageMessage(
              imageFile.getPath(), imageFile.getName(), null, bounds[0], bounds[1]);
      sendMessage(imageMessage, null, null);
    }
  }

  // 发送自定义消息
  public void sendCustomMessage(Map<String, Object> attachment, String content) {
    if (attachment != null) {
      ALog.d(LIB_TAG, TAG, "sendCustomMessage:" + attachment.getClass().getName());
      String attachStr = new JSONObject(attachment).toString();
      V2NIMMessage customMsg = V2NIMMessageCreator.createCustomMessage(content, attachStr);
      sendMessage(customMsg, null, null);
    }
  }

  // 发送转发消息
  public void sendForwardMessage(
      ChatMessageBean message,
      String inputMsg,
      String sessionId,
      V2NIMConversationType sessionType) {
    ALog.d(LIB_TAG, TAG, "sendForwardMessage:" + sessionId);
    if (message != null && !message.isRevoked()) {
      // 转发消息
      V2NIMMessage forwardMessage =
          V2NIMMessageCreator.createForwardMessage(message.getMessageData().getMessage());
      MessageHelper.clearAitAndReplyInfo(forwardMessage);
      sendMessageStrExtension(
          forwardMessage,
          V2NIMConversationIdUtil.conversationId(sessionId, sessionType),
          null,
          null);
      sendNoteMessage(inputMsg, Collections.singletonList(sessionId), sessionType);
    } else {
      ToastX.showShortToast(R.string.chat_message_removed_tip);
    }
  }
  // 发送转发消息
  public void sendForwardMessages(
      String displayName,
      String inputMsg,
      List<String> sessionInfo,
      V2NIMConversationType sessionType,
      List<ChatMessageBean> messages) {
    if (sessionInfo == null || sessionInfo.isEmpty() || messages == null || messages.isEmpty()) {
      return;
    }

    boolean hasError = false;

    // 合并转发消息需要逆序的，所以获取消息列表是逆序。逐条转发需要正序，所以要倒序遍历
    for (int index = messages.size() - 1; index >= 0; index--) {
      ChatMessageBean message = messages.get(index);
      if (message.isRevoked()) {
        continue;
      }
      if (message.getMessageData().getMessage().getMessageType()
          == V2NIMMessageType.V2NIM_MESSAGE_TYPE_AUDIO) {
        hasError = true;
        continue;
      }
      for (String session : sessionInfo) {
        //转发
        V2NIMMessage forwardMessage =
            V2NIMMessageCreator.createForwardMessage(message.getMessageData().getMessage());
        MessageHelper.clearAitAndReplyInfo(forwardMessage);
        sendMessageStrExtension(
            forwardMessage,
            V2NIMConversationIdUtil.conversationId(session, sessionType),
            null,
            null);
      }
    }
    if (hasError) {
      ToastX.showLongToast(R.string.msg_multi_forward_error_tips);
    }
    sendNoteMessage(inputMsg, sessionInfo, sessionType);
  }

  // 发送合并转发消息
  public void sendMultiForwardMessage(
      String displayName,
      String inputMsg,
      List<String> sessionInfo,
      V2NIMConversationType sessionType,
      List<ChatMessageBean> messages) {
    ALog.d(LIB_TAG, TAG, "sendMultiForwardMessage");
    if (sessionInfo == null || sessionInfo.isEmpty() || messages == null || messages.isEmpty()) {
      return;
    }

    List<IMMessageInfo> iMessageList = new ArrayList<>();
    for (ChatMessageBean message : messages) {
      iMessageList.add(message.getMessageData());
    }

    //合并转发消息，序列化消息
    String msgInfo = MessageHelper.createMultiForwardMsg(iMessageList);
    try {

      File localFile = SendMediaHelper.createTextFile();
      //      保存到本地并上传到nos
      ResourceRepo.writeLocalFileAndUploadNOS(
          localFile,
          msgInfo,
          new FetchCallback<>() {
            @Override
            public void onError(int errorCode, @Nullable String errorMsg) {
              ALog.e(
                  LIB_TAG,
                  TAG,
                  "writeLocalFileAndUploadNOS onError:" + errorCode + " errorMsg:" + errorMsg);
            }

            @Override
            public void onSuccess(@Nullable String param) {
              if (param != null) {
                String fileMD5 = EncryptUtils.md5(localFile);
                MultiForwardAttachment attachment =
                    MessageHelper.createMultiTransmitAttachment(
                        displayName, mChatAccountId, param, iMessageList);
                attachment.md5 = fileMD5;
                for (String session : sessionInfo) {
                  V2NIMMessage multiForwardMessage =
                      V2NIMMessageCreator.createCustomMessage(displayName, attachment.toJsonStr());
                  String conversationId =
                      V2NIMConversationIdUtil.conversationId(session, sessionType);
                  sendMessageStrExtension(multiForwardMessage, conversationId, null, null);
                }
                sendNoteMessage(inputMsg, sessionInfo, sessionType);
              }
            }
          });

    } catch (IOException e) {
      ALog.e(LIB_TAG, TAG, "sendMultiForwardMessage IOException:" + e.getMessage());
    }
  }

  /**
   * 发送脚本消息
   *
   * @param inputMsg 输入的脚本消息
   * @param sessions 会话ID列表
   * @param sessionType 会话类型
   */
  private void sendNoteMessage(
      String inputMsg, List<String> sessions, V2NIMConversationType sessionType) {
    if (TextUtils.isEmpty(inputMsg) || TextUtils.getTrimmedLength(inputMsg) <= 0) {
      return;
    }
    //delay 500ms 发送
    new Handler(Looper.getMainLooper())
        .postDelayed(
            () -> {
              for (String sessionId : sessions) {
                V2NIMMessage textMessage = V2NIMMessageCreator.createTextMessage(inputMsg);
                sendMessageStrExtension(
                    textMessage,
                    V2NIMConversationIdUtil.conversationId(sessionId, sessionType),
                    null,
                    null);
              }
            },
            500);
  }

  // 发送位置消息
  public void sendLocationMessage(ChatLocationBean locationBean) {
    ALog.d(LIB_TAG, TAG, "sendLocationMessage:" + locationBean);
    V2NIMMessage locationMsg =
        V2NIMMessageCreator.createLocationMessage(
            locationBean.getLat(), locationBean.getLng(), locationBean.getAddress());
    locationMsg.setText(locationBean.getTitle());
    sendMessage(locationMsg, null, null);
  }

  // 发送视频消息
  public void sendVideoMessage(
      File videoFile, int duration, int width, int height, String displayName) {
    if (videoFile != null) {
      ALog.d(LIB_TAG, TAG, "sendVideoMessage:" + videoFile.getPath());
      V2NIMMessage msg =
          V2NIMMessageCreator.createVideoMessage(
              videoFile.getPath(), displayName, null, duration, width, height);
      sendMessage(msg, null, null);
    }
  }

  // 发送文件消息
  public void sendFileMessage(File docsFile, String displayName) {
    if (docsFile != null) {
      ALog.d(LIB_TAG, TAG, "sendFileMessage:" + docsFile.getPath());
      if (TextUtils.isEmpty(displayName)) {
        displayName = docsFile.getName();
      }
      V2NIMMessage msg =
          V2NIMMessageCreator.createFileMessage(docsFile.getPath(), displayName, null);
      //      IMMessage message =
      //          MessageBuilder.createFileMessage(mSessionId, mSessionType, docsFile, displayName);
      sendMessage(msg, null, null);
    }
  }

  public void sendImageOrVideoMessage(Uri uri) {
    ALog.d(LIB_TAG, TAG, "sendImageOrVideoMessage:" + uri);
    if (uri == null) {
      return;
    }
    String mimeType = FileUtils.getFileExtension(uri.getPath());
    if (TextUtils.isEmpty(mimeType)) {
      try {
        String realPath = UriUtils.uri2FileRealPath(uri);
        mimeType = FileUtils.getFileExtension(realPath);
      } catch (IllegalStateException e) {
        ToastX.showShortToast(R.string.chat_message_type_resource_error);
        return;
      }
    }
    if (ImageUtil.isValidPictureFile(mimeType)) {
      SendMediaHelper.handleImage(uri, true, this::sendImageMessage);
    } else if (ImageUtil.isValidVideoFile(mimeType)) {
      SendMediaHelper.handleVideo(
          uri,
          file -> {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
              mmr.setDataSource(file.getPath());
              String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
              String width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
              String height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
              String orientation =
                  mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
              if (TextUtils.equals(orientation, Orientation_Vertical)) {
                String local = width;
                width = height;
                height = local;
              }
              ALog.d(
                  LIB_TAG,
                  TAG,
                  "width:" + width + "height" + height + "orientation:" + orientation);
              sendVideoMessage(
                  file,
                  Integer.parseInt(duration),
                  Integer.parseInt(width),
                  Integer.parseInt(height),
                  file.getName());
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              try {
                mmr.release();
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          });
    } else {
      ToastX.showShortToast(R.string.chat_message_type_not_support_tips);
      ALog.d(LIB_TAG, TAG, "invalid file type");
    }
  }

  // 发送文件消息
  public void sendFile(Uri uri) {
    ALog.d(LIB_TAG, TAG, "sendFile:" + (uri != null ? uri.getPath() : "uri is null"));
    if (uri == null) {
      return;
    }
    SendMediaHelper.handleFile(
        uri,
        file -> {
          try {
            String displayName = ChatUtils.getUrlFileName(IMKitClient.getApplicationContext(), uri);
            sendFileMessage(file, displayName);
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }

  // 同步发送消息
  private void postMessageSend(IMMessageInfo message, boolean sending) {
    ALog.d(LIB_TAG, TAG, "postMessageSend");
    sendMessageFetchResult.setLoadStatus(LoadStatus.Success);
    if (!sending) {
      sendMessageFetchResult.setType(FetchResult.FetchType.Update);
    } else {
      sendMessageFetchResult.setType(FetchResult.FetchType.Add);
    }
    if (message.getMessage().getMessageType() == V2NIMMessageType.V2NIM_MESSAGE_TYPE_CUSTOM) {
      message.parseAttachment();
    }
    sendMessageFetchResult.setData(new ChatMessageBean(message));
    sendMessageLiveData.setValue(sendMessageFetchResult);
  }

  // 发送消息
  public void sendMessage(
      V2NIMMessage message, List<String> pushList, Map<String, Object> remoteExtension) {
    if (remoteExtension != null) {
      JSONObject jsonObject = new JSONObject(remoteExtension);
      sendMessageStrExtension(message, mConversationId, pushList, jsonObject.toString());
    } else {
      sendMessageStrExtension(message, mConversationId, pushList, null);
    }
  }

  // 发送消息，附带扩展字段
  public void sendMessageStrExtension(
      V2NIMMessage message, String conversationId, List<String> pushList, String remoteExtension) {
    if (message != null) {
      ALog.d(
          LIB_TAG,
          TAG,
          "sendMessage:"
              + message.getMessageClientId()
              + " needACK:"
              + needACK
              + " showRead:"
              + showRead);

      V2NIMMessageConfig.V2NIMMessageConfigBuilder configBuilder =
          V2NIMMessageConfig.V2NIMMessageConfigBuilder.builder();
      configBuilder.withReadReceiptEnabled(needACK && showRead);
      V2NIMMessagePushConfig.V2NIMMessagePushConfigBuilder pushConfigBuilder =
          V2NIMMessagePushConfig.V2NIMMessagePushConfigBuilder.builder();
      if (pushList != null && !pushList.isEmpty()) {
        pushConfigBuilder.withForcePush(true).withForcePushAccountIds(pushList);
      }
      V2NIMSendMessageParams params =
          V2NIMSendMessageParams.V2NIMSendMessageParamsBuilder.builder()
              .withMessageConfig(configBuilder.build())
              .withPushConfig(pushConfigBuilder.build())
              .build();
      //remoteExtension设置
      if (!TextUtils.isEmpty(remoteExtension)) {
        message.setServerExtension(remoteExtension);
      }
      ChatRepo.sendMessage(
          message,
          conversationId,
          params,
          new ProgressFetchCallback<>() {

            @Override
            public void onProgress(int progress) {
              ALog.d(LIB_TAG, TAG, "sendMessage progress -->> " + progress);
            }

            @Override
            public void onSuccess(@Nullable V2NIMSendMessageResult data) {}

            @Override
            public void onError(int errorCode, @Nullable String errorMsg) {
              ALog.d(
                  LIB_TAG, TAG, "sendMessage onError -->> " + errorCode + " errorMsg:" + errorMsg);
              if (errorCode == ChatKitUIConstant.ERROR_CODE_IN_BLACK_LIST) {
                //保存本地黑名单消息
                MessageHelper.saveLocalBlackTipMessageAndNotify(message);
              }
              IMMessageInfo messageInfo = new IMMessageInfo(message);
              messageInfo.setSendingState(
                  V2NIMMessageSendingState.V2NIM_MESSAGE_SENDING_STATE_FAILED);
              postMessageSend(messageInfo, false);
            }
          });
    }
  }

  // 发送已读回执
  public abstract void sendReceipt(V2NIMMessage message);

  // 获取消息列表
  public void getMessageList(V2NIMMessage anchor, boolean needToScrollEnd) {
    ALog.d(LIB_TAG, TAG, "initFetch:" + (anchor == null ? "null" : anchor.getMessageClientId()));
    addListener();
    V2NIMMessageListOption.V2NIMMessageListOptionBuilder optionBuilder =
        V2NIMMessageListOption.V2NIMMessageListOptionBuilder.builder(mConversationId)
            .withLimit(messagePageSize)
            .withDirection(V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_DESC);
    if (anchor == null) {
      ChatRepo.getHistoryMessage(
          optionBuilder.build(),
          new FetchCallback<List<IMMessageInfo>>() {
            @Override
            public void onError(int errorCode, @NonNull String errorMsg) {}

            @Override
            public void onSuccess(@Nullable List<IMMessageInfo> param) {
              if (param != null) {
                Collections.reverse(param);
                //                    fetchPinInfo();
                onListFetchSuccess(param, V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_DESC);
              }
              if (!hasLoadMessage) {
                hasLoadMessage = true;
              }
            }
          });
    } else {
      fetchMessageListBothDirect(anchor, needToScrollEnd);
    }
  }

  /** called when entering the chat page */
  public void getMessageList(V2NIMMessage anchor) {
    getMessageList(anchor, true);
  }

  // 获取更多消息列表
  public void fetchMoreMessage(
      V2NIMMessage anchor, V2NIMMessageQueryDirection direction, boolean needToScrollEnd) {
    ALog.d(LIB_TAG, TAG, "fetchMoreMessage:" + " direction:" + direction);

    V2NIMMessageListOption.V2NIMMessageListOptionBuilder optionBuilder =
        V2NIMMessageListOption.V2NIMMessageListOptionBuilder.builder(mConversationId)
            .withLimit(messagePageSize)
            .withAnchorMessage(anchor)
            .withDirection(direction);

    if (direction == V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_DESC) {
      optionBuilder.withEndTime(anchor.getCreateTime());
    } else {
      optionBuilder.withBeginTime(anchor.getCreateTime());
    }

    ChatRepo.getHistoryMessage(
        optionBuilder.build(),
        new FetchCallback<List<IMMessageInfo>>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            onListFetchFailed(errorCode);
            ALog.d(LIB_TAG, TAG, "fetchMoreMessage:" + errorCode + " errorMsg:" + errorMsg);
          }

          @Override
          public void onSuccess(@Nullable List<IMMessageInfo> data) {
            if (data != null && !data.isEmpty()) {
              if (direction == V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_DESC) {
                Collections.reverse(data);
              }
              ALog.d(LIB_TAG, TAG, "fetchMoreMessage,reverse:" + data.size());
              onListFetchSuccess(anchor, needToScrollEnd, data, direction);
            }
          }
        });
  }

  public void fetchMoreMessage(V2NIMMessage anchor, V2NIMMessageQueryDirection direction) {
    fetchMoreMessage(anchor, direction, true);
  }

  public void fetchMessageListBothDirect(V2NIMMessage anchor, boolean needToScrollEnd) {
    ALog.d(LIB_TAG, TAG, "fetchMessageListBothDirect");
    // 此处避免在获取 anchor 消息后被之前消息添加导致ui移位，因此将 anchor 之前消息请求添加到后续的主线程事件队列中
    new Handler(Looper.getMainLooper())
        .post(
            () ->
                fetchMoreMessage(
                    anchor,
                    V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_DESC,
                    needToScrollEnd));
    fetchMoreMessage(anchor, V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_ASC, needToScrollEnd);
  }

  private void onListFetchSuccess(List<IMMessageInfo> param, V2NIMMessageQueryDirection direction) {
    onListFetchSuccess(null, true, param, direction);
  }

  private void onListFetchSuccess(
      V2NIMMessage anchorMsg,
      boolean needToScrollEnd,
      List<IMMessageInfo> param,
      V2NIMMessageQueryDirection direction) {
    ALog.d(
        LIB_TAG,
        TAG,
        "onListFetchSuccess -->> size:"
            + (param == null ? "null" : param.size())
            + " direction:"
            + direction);

    LoadStatus loadStatus =
        (param == null || param.size() == 0) ? LoadStatus.Finish : LoadStatus.Success;
    messageFetchResult.setLoadStatus(loadStatus);
    messageFetchResult.setData(convert(param));
    if (anchorMsg != null && !needToScrollEnd) {
      messageFetchResult.setExtraInfo(new AnchorScrollInfo(anchorMsg));
    }
    messageFetchResult.setTypeIndex(
        direction == V2NIMMessageQueryDirection.V2NIM_QUERY_DIRECTION_DESC ? 0 : -1);
    messageLiveData.setValue(messageFetchResult);
    if (anchorMsg == null) {
      //首次加载消息，获取消息的发送者信息
      getTeamMemberInfoWithMessage(param);
    }
  }

  protected void getTeamMemberInfoWithMessage(List<IMMessageInfo> messages) {}

  private void onListFetchFailed(int code) {
    ALog.d(LIB_TAG, TAG, "onListFetchFailed code:" + code);
    messageFetchResult.setError(code, R.string.chat_message_fetch_error);
    messageFetchResult.setData(null);
    messageFetchResult.setTypeIndex(-1);
    messageLiveData.setValue(messageFetchResult);
  }

  private List<ChatMessageBean> convert(List<IMMessageInfo> messageList) {
    if (messageList == null) {
      return null;
    }
    ArrayList<ChatMessageBean> result = new ArrayList<>(messageList.size());
    for (IMMessageInfo message : messageList) {
      result.add(new ChatMessageBean(message));
    }
    return result;
  }

  // **********reply message**************
  public void replyMessage(
      V2NIMMessage message,
      V2NIMMessage replyMsg,
      List<String> pushList,
      Map<String, Object> remoteExtension) {
    ALog.d(
        LIB_TAG,
        TAG,
        "replyMessage,message" + (message == null ? "null" : message.getMessageClientId()));
    if (message == null) {
      return;
    }
    //设置remoteExtension
    Map<String, Object> remote = MessageHelper.createReplyExtension(remoteExtension, replyMsg);
    sendMessage(message, pushList, remote);
  }

  public void replyTextMessage(
      String content,
      V2NIMMessage message,
      List<String> pushList,
      Map<String, Object> remoteExtension) {
    ALog.d(
        LIB_TAG,
        TAG,
        "replyTextMessage,message" + (message == null ? "null" : message.getMessageClientId()));
    V2NIMMessage textMessage = V2NIMMessageCreator.createTextMessage(content);
    replyMessage(textMessage, message, pushList, remoteExtension);
  }

  // ********************Message Pin********************

  private final MutableLiveData<Pair<String, V2NIMMessagePin>> addPinMessageLiveData =
      new MutableLiveData<>();

  private final MutableLiveData<String> removePinMessageLiveData = new MutableLiveData<>();

  public MutableLiveData<Pair<String, V2NIMMessagePin>> getAddPinMessageLiveData() {
    return addPinMessageLiveData;
  }

  public MutableLiveData<String> getRemovePinMessageLiveData() {
    return removePinMessageLiveData;
  }

  public void addMessagePin(IMMessageInfo messageInfo, String ext) {
    if (messageInfo == null) {
      return;
    }
    ALog.d(LIB_TAG, TAG, "addMessagePin,message" + messageInfo.getMessage().getMessageClientId());
    ChatRepo.addMessagePin(
        messageInfo.getMessage(),
        ext,
        new FetchCallback<Void>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            if (errorCode == ChatKitUIConstant.ERROR_CODE_PIN_MSG_LIMIT) {
              ToastX.showShortToast(R.string.chat_pin_limit_tips);
            }
            ALog.d(LIB_TAG, TAG, "addMessagePin,onError" + errorCode + " errorMsg:" + errorMsg);
          }

          @Override
          public void onSuccess(@Nullable Void data) {
            ALog.d(
                LIB_TAG,
                TAG,
                "addMessagePin, message onSuccess" + messageInfo.getMessage().getMessageClientId());
          }
        });
  }

  public void removeMsgPin(IMMessageInfo messageInfo) {
    if (messageInfo == null || messageInfo.getPinOption() == null) {
      return;
    }
    ALog.d(LIB_TAG, TAG, "removeMsgPin,message" + messageInfo.getMessage().getMessageClientId());
    ChatRepo.removeMessagePin(messageInfo.getPinOption().getMessageRefer(), null);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    removeListener();
  }
}
