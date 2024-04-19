// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.chatkit.ui.page.viewmodel;

import static com.netease.yunxin.kit.chatkit.ui.ChatKitUIConstant.LIB_TAG;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.netease.nimlib.sdk.v2.conversation.enums.V2NIMConversationType;
import com.netease.nimlib.sdk.v2.message.V2NIMMessage;
import com.netease.nimlib.sdk.v2.message.V2NIMP2PMessageReadReceipt;
import com.netease.nimlib.sdk.v2.notification.V2NIMBroadcastNotification;
import com.netease.nimlib.sdk.v2.notification.V2NIMCustomNotification;
import com.netease.nimlib.sdk.v2.notification.V2NIMNotificationListener;
import com.netease.nimlib.sdk.v2.notification.config.V2NIMNotificationConfig;
import com.netease.nimlib.sdk.v2.notification.config.V2NIMNotificationPushConfig;
import com.netease.nimlib.sdk.v2.notification.params.V2NIMSendCustomNotificationParams;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.repo.ChatRepo;
import com.netease.yunxin.kit.chatkit.repo.ContactRepo;
import com.netease.yunxin.kit.chatkit.ui.common.ChatUserCache;
import com.netease.yunxin.kit.common.ui.viewmodel.FetchResult;
import com.netease.yunxin.kit.common.ui.viewmodel.LoadStatus;
import com.netease.yunxin.kit.corekit.im2.IMKitClient;
import com.netease.yunxin.kit.corekit.im2.extend.FetchCallback;
import com.netease.yunxin.kit.corekit.im2.model.UserWithFriend;
import com.netease.yunxin.kit.corekit.im2.model.V2UserInfo;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** P2P chat info view model message receipt, type state for P2P chat page */
public class ChatP2PViewModel extends ChatBaseViewModel {

  private static final String TAG = "ChatP2PViewModel";

  private static final String TYPE_STATE = "typing";

  private long receiptTime = 0L;

  private final MutableLiveData<V2NIMP2PMessageReadReceipt> messageReceiptLiveData =
      new MutableLiveData<>();

  private final MutableLiveData<Boolean> typeStateLiveData = new MutableLiveData<>();

  //用户信息数据
  private final MutableLiveData<FetchResult<UserWithFriend>> friendInfoLiveData =
      new MutableLiveData<>();

  private final FetchResult<UserWithFriend> friendInfoFetchResult =
      new FetchResult<>(LoadStatus.Finish);

  private final V2NIMNotificationListener notificationListener =
      new V2NIMNotificationListener() {
        @Override
        public void onReceiveCustomNotifications(
            List<V2NIMCustomNotification> customNotifications) {
          ALog.d(
              LIB_TAG,
              TAG,
              "mcustomNotificationObserver:"
                  + (customNotifications == null ? "null" : customNotifications.size()));
          if (customNotifications == null) {
            return;
          }
          for (V2NIMCustomNotification notification : customNotifications) {
            if (!TextUtils.equals(notification.getReceiverId(), IMKitClient.account())
                || notification.getConversationType()
                    != V2NIMConversationType.V2NIM_CONVERSATION_TYPE_P2P) {
              return;
            }
            String content = notification.getContent();
            try {
              JSONObject json = new JSONObject(content);
              int id = json.getInt(TYPE_STATE);
              if (id == 1) {
                typeStateLiveData.postValue(true);
              } else {
                typeStateLiveData.postValue(false);
              }
            } catch (JSONException e) {
              ALog.e(TAG, e.getMessage());
            }
          }
        }

        @Override
        public void onReceiveBroadcastNotifications(
            List<V2NIMBroadcastNotification> broadcastNotifications) {
          //do nothing
        }
      };

  /** chat message read receipt live data */
  public MutableLiveData<V2NIMP2PMessageReadReceipt> getMessageReceiptLiveData() {
    return messageReceiptLiveData;
  }

  /** chat typing state live data */
  public MutableLiveData<Boolean> getTypeStateLiveData() {
    return typeStateLiveData;
  }

  public MutableLiveData<FetchResult<UserWithFriend>> getFriendInfoLiveData() {
    return friendInfoLiveData;
  }

  public void getP2PData(V2NIMMessage anchorMessage) {
    getFriendInfo(mChatAccountId);
    getMessageList(anchorMessage, false);
  }

  public void getFriendInfo(String accId) {
    ALog.d(LIB_TAG, TAG, "getFriendInfo:" + accId);
    List<String> accounts = new ArrayList<>();
    accounts.add(IMKitClient.account());
    accounts.add(accId);
    ContactRepo.getFriend(
        accId,
        false,
        new FetchCallback<>() {

          @Override
          public void onSuccess(@Nullable UserWithFriend data) {
            if (data != null) {
              ChatUserCache.getInstance().addUserInfo(new V2UserInfo(accId, data.getUserInfo()));
              friendInfoFetchResult.setData(data);
              friendInfoFetchResult.setLoadStatus(LoadStatus.Success);
              friendInfoLiveData.setValue(friendInfoFetchResult);
            }
          }

          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {}
        });
  }

  @Override
  protected void onP2PMessageReadReceipts(List<V2NIMP2PMessageReadReceipt> readReceipts) {
    super.onP2PMessageReadReceipts(readReceipts);
    ALog.d(
        LIB_TAG, TAG, "message receipt:" + (readReceipts == null ? "null" : readReceipts.size()));
    FetchResult<List<V2NIMP2PMessageReadReceipt>> receiptResult =
        new FetchResult<>(LoadStatus.Finish);
    receiptResult.setData(readReceipts);
    receiptResult.setType(FetchResult.FetchType.Update);
    receiptResult.setTypeIndex(-1);
    if (receiptResult.getData() != null) {
      for (V2NIMP2PMessageReadReceipt receiptInfo : receiptResult.getData()) {
        if (TextUtils.equals(receiptInfo.getConversationId(), mConversationId)) {
          messageReceiptLiveData.setValue(receiptInfo);
        }
      }
    }
  }

  @Override
  public void addListener() {
    super.addListener();
    ChatRepo.addNotificationListener(notificationListener);
  }

  @Override
  public void removeListener() {
    super.removeListener();
    ChatRepo.removeNotificationListener(notificationListener);
  }

  @Override
  public void sendReceipt(V2NIMMessage message) {
    ALog.d(
        LIB_TAG,
        TAG,
        "sendReceipt:"
            + (message == null
                ? "null"
                : message.getMessageClientId()
                    + message.getMessageConfig().isReadReceiptEnabled()));
    if (message != null
        && message.getMessageConfig().isReadReceiptEnabled()
        && showRead
        && message.getCreateTime() > receiptTime) {
      receiptTime = message.getCreateTime();
      ChatRepo.markP2PMessageRead(message);
    }
  }

  @Override
  public void notifyFriendChange(UserWithFriend friend) {
    if (friend.getAccount().equals(mChatAccountId)) {
      friendInfoFetchResult.setData(friend);
      friendInfoFetchResult.setLoadStatus(LoadStatus.Success);
      friendInfoLiveData.setValue(friendInfoFetchResult);
    }
  }

  public void sendInputNotification(boolean isTyping) {
    ALog.d(LIB_TAG, TAG, "sendInputNotification:" + isTyping);

    try {
      JSONObject json = new JSONObject();
      json.put(TYPE_STATE, isTyping ? 1 : 0);
      String content = json.toString();
      V2NIMNotificationPushConfig pushConfig =
          V2NIMNotificationPushConfig.V2NIMNotificationPushConfigBuilder.builder()
              .withPushEnabled(false)
              .build();

      V2NIMNotificationConfig notificationConfig =
          V2NIMNotificationConfig.V2NIMNotificationConfigBuilder.builder()
              .withUnreadEnabled(false)
              .withOfflineEnabled(false)
              .build();

      V2NIMSendCustomNotificationParams params =
          V2NIMSendCustomNotificationParams.V2NIMSendCustomNotificationParamsBuilder.builder()
              .withPushConfig(pushConfig)
              .withNotificationConfig(notificationConfig)
              .build();

      ChatRepo.sendCustomNotification(mConversationId, content, params, null);

    } catch (JSONException e) {
      ALog.e(TAG, e.getMessage());
    }
  }
}
