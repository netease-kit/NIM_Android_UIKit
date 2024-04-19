// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.conversationkit.ui.page.viewmodel;

import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.netease.nimlib.sdk.v2.V2NIMError;
import com.netease.nimlib.sdk.v2.conversation.V2NIMConversationListener;
import com.netease.nimlib.sdk.v2.conversation.enums.V2NIMConversationType;
import com.netease.nimlib.sdk.v2.conversation.model.V2NIMConversation;
import com.netease.nimlib.sdk.v2.conversation.result.V2NIMConversationResult;
import com.netease.nimlib.sdk.v2.team.V2NIMTeamListener;
import com.netease.nimlib.sdk.v2.team.model.V2NIMTeam;
import com.netease.nimlib.sdk.v2.utils.V2NIMConversationIdUtil;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.impl.ConversationListenerImpl;
import com.netease.yunxin.kit.chatkit.impl.TeamListenerImpl;
import com.netease.yunxin.kit.chatkit.repo.ConversationRepo;
import com.netease.yunxin.kit.chatkit.repo.TeamRepo;
import com.netease.yunxin.kit.chatkit.utils.ErrorUtils;
import com.netease.yunxin.kit.common.ui.utils.ToastX;
import com.netease.yunxin.kit.common.ui.viewmodel.BaseViewModel;
import com.netease.yunxin.kit.common.ui.viewmodel.FetchResult;
import com.netease.yunxin.kit.common.ui.viewmodel.LoadStatus;
import com.netease.yunxin.kit.conversationkit.ui.IConversationFactory;
import com.netease.yunxin.kit.conversationkit.ui.R;
import com.netease.yunxin.kit.conversationkit.ui.common.ConversationConstant;
import com.netease.yunxin.kit.conversationkit.ui.common.ConversationUtils;
import com.netease.yunxin.kit.conversationkit.ui.model.ConversationBean;
import com.netease.yunxin.kit.conversationkit.ui.page.DefaultViewHolderFactory;
import com.netease.yunxin.kit.corekit.event.EventCenter;
import com.netease.yunxin.kit.corekit.event.EventNotify;
import com.netease.yunxin.kit.corekit.im2.IMKitClient;
import com.netease.yunxin.kit.corekit.im2.custom.AitEvent;
import com.netease.yunxin.kit.corekit.im2.custom.AitInfo;
import com.netease.yunxin.kit.corekit.im2.extend.FetchCallback;
import com.netease.yunxin.kit.corekit.im2.utils.RouterConstant;
import com.netease.yunxin.kit.corekit.route.XKitRouter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 会话列表逻辑层ViewModel */
public class ConversationViewModel extends BaseViewModel {

  private final String TAG = "ConversationViewModel";
  private final String LIB_TAG = "ConversationKit-UI";

  // 未读数LiveData，用于通知未读数变化
  private final MutableLiveData<FetchResult<Integer>> unreadCountLiveData = new MutableLiveData<>();
  // 会话列表LiveData，用于通知会话列表查询结果
  private final MutableLiveData<FetchResult<List<ConversationBean>>> queryLiveData =
      new MutableLiveData<>();
  // 会话变化LiveData，用于通知会话信息变化变更
  private final MutableLiveData<FetchResult<List<ConversationBean>>> changeLiveData =
      new MutableLiveData<>();
  // @信息LiveData，用于通知@信息变更
  private final MutableLiveData<FetchResult<List<String>>> aitLiveData = new MutableLiveData<>();
  // 删除会话LiveData，用于通知会话删除结果
  private final MutableLiveData<FetchResult<List<String>>> deleteLiveData = new MutableLiveData<>();

  // 会话列表排序比较器
  private Comparator<ConversationBean> comparator;
  private IConversationFactory conversationFactory = new DefaultViewHolderFactory();
  // 分页加载，每页加载数量
  private static final int PAGE_LIMIT = 50;
  // 分页加载，当前加载偏移量
  private long mOffset = 0;
  // 分页加载，是否还有更多数据
  private boolean hasMore = true;
  // 数据查询是否已经开始
  private boolean hasStart = false;

  public ConversationViewModel() {
    // 注册会话监听
    ConversationRepo.addConversationListener(conversationListener);
    // 注册群组监听,用于监听群解散和退出
    TeamRepo.addTeamListener(teamListener);
    // 注册@信息监听,业务层逻辑
    EventNotify<AitEvent> aitNotify =
        new EventNotify<AitEvent>() {
          @Override
          public void onNotify(@NonNull AitEvent aitEvent) {
            FetchResult<List<String>> result = new FetchResult<>(LoadStatus.Finish);
            ALog.d(LIB_TAG, TAG, "aitNotify");
            if (aitEvent.getAitInfoList() == null) {
              return;
            }
            if (aitEvent.getEventType() == AitEvent.AitEventType.Arrive
                || aitEvent.getEventType() == AitEvent.AitEventType.Load) {
              result.setFetchType(FetchResult.FetchType.Add);
            } else {
              result.setFetchType(FetchResult.FetchType.Remove);
            }
            List<AitInfo> aitInfoList = aitEvent.getAitInfoList();
            List<String> sessionIdList = new ArrayList<>();
            for (AitInfo info : aitInfoList) {
              sessionIdList.add(info.getConversationId());
            }
            result.setData(sessionIdList);
            aitLiveData.setValue(result);
          }

          @NonNull
          @Override
          public String getEventType() {
            return "AitEvent";
          }
        };
    EventCenter.registerEventNotify(aitNotify);
  }

  // 设置会话列表排序比较器
  public void setComparator(Comparator<ConversationBean> comparator) {
    this.comparator = comparator;
  }

  // 设置会话列表ViewHolder工厂
  public void setConversationFactory(IConversationFactory factory) {
    this.conversationFactory = factory;
  }

  public MutableLiveData<FetchResult<Integer>> getUnreadCountLiveData() {
    return unreadCountLiveData;
  }

  public MutableLiveData<FetchResult<List<ConversationBean>>> getQueryLiveData() {
    return queryLiveData;
  }

  public MutableLiveData<FetchResult<List<ConversationBean>>> getChangeLiveData() {
    return changeLiveData;
  }

  public MutableLiveData<FetchResult<List<String>>> getDeleteLiveData() {
    return deleteLiveData;
  }

  public MutableLiveData<FetchResult<List<String>>> getAitLiveData() {
    return aitLiveData;
  }

  /** 获取未读数 */
  public void getUnreadCount() {
    int unreadCount = ConversationRepo.getUnreadCount();
    ALog.d(LIB_TAG, TAG, "getUnreadCount,onSuccess:" + unreadCount);
    FetchResult<Integer> fetchResult = new FetchResult<>(LoadStatus.Success);
    fetchResult.setData(unreadCount);
    unreadCountLiveData.setValue(fetchResult);
  }

  /** 获取会话列表相关数据，包括会话列表数据和未读数 */
  public void getConversationData() {
    XKitRouter.withKey(RouterConstant.PATH_CHAT_AIT_NOTIFY_ACTION).navigate();
    getConversationByPage(0);
    getUnreadCount();
  }

  /** 加载更多会话列表数据 */
  public void loadMore() {
    ALog.d(LIB_TAG, TAG, "loadMore:");
    getConversationByPage(mOffset);
  }

  /**
   * 查询会话列表
   *
   * @param offSet 偏移量
   */
  private void getConversationByPage(long offSet) {
    ALog.d(LIB_TAG, TAG, "queryConversation:" + offSet);
    if (hasStart) {
      ALog.d(LIB_TAG, TAG, "queryConversation,has Started return");
      return;
    }
    hasStart = true;
    ConversationRepo.getConversationList(
        offSet,
        PAGE_LIMIT,
        new FetchCallback<>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            ALog.e(LIB_TAG, TAG, "queryConversation,onError:" + errorCode + "," + errorMsg);
            hasStart = false;
          }

          @Override
          public void onSuccess(@Nullable V2NIMConversationResult data) {
            ALog.d(
                LIB_TAG,
                TAG,
                "queryConversation,onSuccess:"
                    + ((data != null && data.getConversationList() != null)
                        ? data.getConversationList().size()
                        : 0));
            FetchResult<List<ConversationBean>> result = new FetchResult<>(LoadStatus.Success);
            result.setType(offSet > 0 ? FetchResult.FetchType.Add : FetchResult.FetchType.Init);

            if (data != null && data.getConversationList() != null) {
              checkConversationAndRemove(data.getConversationList());
              List<ConversationBean> resultData =
                  createConversationBean(data.getConversationList());
              if (comparator != null) {
                Collections.sort(resultData, comparator);
              }
              result.setData(resultData);
              hasMore = resultData.size() == PAGE_LIMIT;
              mOffset = data.getOffset();
            }
            queryLiveData.setValue(result);
            hasStart = false;
          }
        });
  }

  /**
   * 删除会话
   *
   * @param conversationId 会话ID
   */
  public void deleteConversation(String conversationId) {
    ConversationRepo.deleteConversation(
        conversationId,
        false,
        new FetchCallback<>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            ALog.d(LIB_TAG, TAG, "deleteConversation,onError:" + errorCode + "," + errorMsg);
            ErrorUtils.showErrorCodeToast(IMKitClient.getApplicationContext(), errorCode);
          }

          @Override
          public void onSuccess(@Nullable Void data) {
            ALog.d(LIB_TAG, TAG, "deleteConversation,onSuccess:" + conversationId);
          }
        });
  }

  /** 置顶会话 */
  public void addStickTop(ConversationBean param) {

    ConversationRepo.addStickTop(
        param.getConversationId(),
        new FetchCallback<Void>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            ALog.d(LIB_TAG, TAG, "addStickTop,onFailed:" + errorCode);
            if (errorCode == ConversationConstant.ERROR_CODE_NETWORK) {
              ToastX.showShortToast(R.string.conversation_network_error_tip);
            }
          }

          @Override
          public void onSuccess(@Nullable Void data) {
            ALog.d(LIB_TAG, TAG, "addStickTop,onSuccess:" + param.getConversationId());
          }
        });
  }

  /**
   * 取消置顶会话
   *
   * @param conversationBean 会话信息
   */
  public void removeStick(ConversationBean conversationBean) {
    ConversationRepo.removeStickTop(
        conversationBean.getConversationId(),
        new FetchCallback<Void>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            ALog.d(LIB_TAG, TAG, "removeStick,onFailed:" + errorCode);
            if (errorCode == ConversationConstant.ERROR_CODE_NETWORK) {
              ToastX.showShortToast(R.string.conversation_network_error_tip);
            }
          }

          @Override
          public void onSuccess(@Nullable Void data) {
            ALog.d(LIB_TAG, TAG, "removeStick,onSuccess:" + conversationBean.getConversationId());
          }
        });
  }

  // 会话监听Listener
  private final V2NIMConversationListener conversationListener =
      new ConversationListenerImpl() {

        @Override
        public void onSyncFinished() {
          ALog.d(LIB_TAG, TAG, "onSyncFinished:");
          getConversationData();
        }

        @Override
        public void onSyncFailed(V2NIMError error) {
          ALog.d(LIB_TAG, TAG, "onSyncFailed:");
        }

        @Override
        public void onConversationCreated(V2NIMConversation conversation) {
          ALog.d(
              LIB_TAG,
              TAG,
              "onConversationCreated,conversation:"
                  + (conversation != null ? conversation.getConversationId() : "id is null"));
          FetchResult<List<ConversationBean>> result = new FetchResult<>(LoadStatus.Success);
          result.setType(FetchResult.FetchType.Add);
          List<V2NIMConversation> data = new ArrayList<>();
          data.add(conversation);
          convertAndNotify(result, data);
        }

        @Override
        public void onConversationDeleted(List<String> conversationIds) {
          FetchResult<List<String>> result = new FetchResult<>(LoadStatus.Success);
          result.setData(conversationIds);
          ALog.d(LIB_TAG, TAG, "onConversationDeleted,onSuccess");
          deleteLiveData.setValue(result);
        }

        @Override
        public void onConversationChanged(List<V2NIMConversation> conversationList) {
          ALog.d(
              LIB_TAG,
              TAG,
              "onConversationChanged,conversation:"
                  + (conversationList != null ? conversationList.size() : "0"));
          if (conversationList == null || conversationList.isEmpty()) {
            return;
          }
          List<V2NIMConversation> changeList = new ArrayList<>();
          List<String> deleteList = new ArrayList<>();
          for (V2NIMConversation conversation : conversationList) {
            ALog.d(
                LIB_TAG,
                TAG,
                "onConversationChanged,conversation:"
                    + (conversation != null ? conversation.getConversationId() : "id is null"));
            if (conversation != null
                && ConversationUtils.isDismissTeamMsg(conversation.getLastMessage())) {
              deleteList.add(conversation.getConversationId());
              deleteConversation(conversation.getConversationId());
            } else {
              changeList.add(conversation);
            }
          }
          if (changeList.size() > 0) {
            ALog.d(LIB_TAG, TAG, "onConversationChanged,changeList:" + changeList.size());
            FetchResult<List<ConversationBean>> result = new FetchResult<>(LoadStatus.Success);
            result.setType(FetchResult.FetchType.Update);
            convertAndNotify(result, changeList);
          }
          if (deleteList.size() > 0) {
            ALog.d(LIB_TAG, TAG, "onConversationChanged,deleteList:" + deleteList.size());
            FetchResult<List<String>> result = new FetchResult<>(LoadStatus.Success);
            result.setData(deleteList);
            deleteLiveData.setValue(result);
          }
        }

        @Override
        public void onTotalUnreadCountChanged(int unreadCount) {
          ALog.d(LIB_TAG, TAG, "onTotalUnreadCountChanged:" + unreadCount);
          FetchResult<Integer> result = new FetchResult<>(LoadStatus.Success);
          result.setData(unreadCount);
          unreadCountLiveData.setValue(result);
        }
      };

  // 群组监听Listener
  private final V2NIMTeamListener teamListener =
      new TeamListenerImpl() {

        @Override
        public void onTeamDismissed(@Nullable V2NIMTeam team) {
          if (team == null) {
            return;
          }
          ALog.d(LIB_TAG, TAG, "onTeamDismissed:" + team.getTeamId());
          String conversationId =
              V2NIMConversationIdUtil.conversationId(
                  team.getTeamId(), V2NIMConversationType.V2NIM_CONVERSATION_TYPE_TEAM);
          delayPostDelete(conversationId);
        }

        @Override
        public void onTeamLeft(@Nullable V2NIMTeam team, boolean isKicked) {
          if (team == null) {
            return;
          }
          ALog.d(LIB_TAG, TAG, "onTeamLeft:" + team.getTeamId());
          String conversationId =
              V2NIMConversationIdUtil.conversationId(
                  team.getTeamId(), V2NIMConversationType.V2NIM_CONVERSATION_TYPE_TEAM);
          delayPostDelete(conversationId);
        }
      };

  //todo 等待SDK 修复群通知和群解散通知时序之后，可以去除该方法
  private void delayPostDelete(final String conversationId) {
    new Handler()
        .postDelayed(
            () -> {
              deleteConversation(conversationId);
              FetchResult<List<String>> result = new FetchResult<>(LoadStatus.Success);
              result.setData(Collections.singletonList(conversationId));
              ALog.d(LIB_TAG, TAG, "onTeamDismissed delayPostDelete");
              deleteLiveData.setValue(result);
            },
            500);
  }

  // 会话列表转换并通知LiveData
  public void convertAndNotify(
      FetchResult<List<ConversationBean>> result, List<V2NIMConversation> conversationList) {
    if (conversationList != null) {
      List<ConversationBean> resultData = createConversationBean(conversationList);
      result.setData(resultData);
      changeLiveData.setValue(result);
    }
  }

  //工具方法，将会话信息转换为会话列表数据
  public List<ConversationBean> createConversationBean(List<V2NIMConversation> data) {
    List<ConversationBean> resultData = new ArrayList<>();
    if (data != null) {
      for (int index = 0; index < data.size(); index++) {
        resultData.add(conversationFactory.CreateBean(data.get(index)));
      }
    }
    return resultData;
  }

  //工具方法，去除重复会话
  public void checkConversationAndRemove(List<V2NIMConversation> data) {
    Set<String> conversationIds = new HashSet<>();
    if (data != null) {
      for (int index = 0; index < data.size(); index++) {
        if (conversationIds.contains(data.get(index).getConversationId())) {
          ALog.d(
              LIB_TAG,
              TAG,
              "checkConversationAndRemove,remove:" + data.get(index).getConversationId());
          data.remove(index);
          index--;
        } else {
          conversationIds.add(data.get(index).getConversationId());
        }
      }
    }
  }

  // 是否还有更多数据
  public boolean hasMore() {
    return hasMore;
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    ConversationRepo.removeConversationListener(conversationListener);
    TeamRepo.removeTeamListener(teamListener);
  }
}
