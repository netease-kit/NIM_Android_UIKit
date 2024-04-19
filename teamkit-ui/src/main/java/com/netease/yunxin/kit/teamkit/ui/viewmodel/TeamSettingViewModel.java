// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.teamkit.ui.viewmodel;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.netease.nimlib.sdk.v2.conversation.model.V2NIMConversation;
import com.netease.nimlib.sdk.v2.setting.enums.V2NIMTeamMessageMuteMode;
import com.netease.nimlib.sdk.v2.team.enums.V2NIMTeamType;
import com.netease.nimlib.sdk.v2.team.model.V2NIMTeam;
import com.netease.nimlib.sdk.v2.utils.V2NIMConversationIdUtil;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.model.TeamMemberListResult;
import com.netease.yunxin.kit.chatkit.model.TeamMemberWithUserInfo;
import com.netease.yunxin.kit.chatkit.repo.ConversationRepo;
import com.netease.yunxin.kit.chatkit.repo.TeamRepo;
import com.netease.yunxin.kit.chatkit.utils.ChatKitConstant;
import com.netease.yunxin.kit.common.ui.viewmodel.FetchResult;
import com.netease.yunxin.kit.corekit.event.EventCenter;
import com.netease.yunxin.kit.corekit.im2.IMKitClient;
import com.netease.yunxin.kit.corekit.im2.custom.TeamEvent;
import com.netease.yunxin.kit.corekit.im2.custom.TeamEventAction;
import com.netease.yunxin.kit.corekit.im2.extend.FetchCallback;
import java.util.Objects;

/** 群相关业务逻辑 提供数据查询接口 提供群变更监听 */
public class TeamSettingViewModel extends TeamBaseViewModel {

  private static final String TAG = "TeamSettingViewModel";
  private static final String LIB_TAG = "TeamKit-UI";

  private final MutableLiveData<FetchResult<String>> nameData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<String>> introduceData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<String>> nicknameData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<String>> iconData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<Void>> quitTeamData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<Void>> dismissTeamData = new MutableLiveData<>();

  private final MutableLiveData<FetchResult<Boolean>> notifyData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<Boolean>> stickData = new MutableLiveData<>();
  private final MutableLiveData<FetchResult<Boolean>> muteTeamAllMemberData =
      new MutableLiveData<>();

  public MutableLiveData<FetchResult<Boolean>> getMuteTeamAllMemberData() {
    return muteTeamAllMemberData;
  }

  public MutableLiveData<FetchResult<String>> getNameData() {
    return nameData;
  }

  public MutableLiveData<FetchResult<String>> getIntroduceData() {
    return introduceData;
  }

  public MutableLiveData<FetchResult<String>> getNicknameData() {
    return nicknameData;
  }

  public MutableLiveData<FetchResult<String>> getIconData() {
    return iconData;
  }

  public MutableLiveData<FetchResult<Void>> getQuitTeamData() {
    return quitTeamData;
  }

  public MutableLiveData<FetchResult<Void>> getDismissTeamData() {
    return dismissTeamData;
  }

  public MutableLiveData<FetchResult<Boolean>> getNotifyData() {
    return notifyData;
  }

  public MutableLiveData<FetchResult<Boolean>> getStickData() {
    return stickData;
  }

  public void requestTeamSettingData(String teamId) {
    ALog.d(LIB_TAG, TAG, "requestTeamSettingData:" + teamId);
    requestTeamData(teamId);
    requestTeamMembers(teamId);
    requestStickAndNotify(teamId);
  }

  public void requestStickAndNotify(String teamId) {
    ALog.d(LIB_TAG, TAG, "requestStickAndNotify:" + teamId);
    ConversationRepo.getConversationById(
        V2NIMConversationIdUtil.teamConversationId(teamId),
        new FetchCallback<>() {
          @Override
          public void onError(int errorCode, @Nullable String errorMsg) {
            ALog.d(LIB_TAG, TAG, "requestStickAndNotify,onFailed:" + errorCode);
          }

          @Override
          public void onSuccess(@Nullable V2NIMConversation data) {
            if (data != null) {
              FetchResult<Boolean> stickResult = new FetchResult<>(data.isStickTop());
              stickResult.setType(FetchResult.FetchType.Update);
              stickData.setValue(stickResult);
              FetchResult<Boolean> notifyResult = new FetchResult<>(!data.isMute());
              notifyResult.setType(FetchResult.FetchType.Update);
              notifyData.setValue(notifyResult);
              ALog.d(
                  LIB_TAG,
                  TAG,
                  "requestStickAndNotify,onSuccess,isStickTop:"
                      + data.isStickTop()
                      + ",isMute:"
                      + data.isMute());
            }
          }
        });
  }

  /**
   * 更新群名称
   *
   * @param teamId 群ID
   * @param name 群名称
   */
  public void updateName(String teamId, String name) {
    ALog.d(LIB_TAG, TAG, "updateName:" + teamId);
    TeamRepo.updateTeamName(
        teamId,
        name,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "updateName,onSuccess");
            nameData.setValue(new FetchResult<>(name));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "updateName,onFailed:" + errorCode);
            nameData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 更新群介绍
   *
   * @param teamId 群ID
   * @param introduce 群介绍
   */
  public void updateIntroduce(String teamId, String introduce) {
    ALog.d(LIB_TAG, TAG, "updateIntroduce:" + teamId);
    TeamRepo.updateTeamIntroduce(
        teamId,
        introduce,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "updateIntroduce,onSuccess");
            introduceData.setValue(new FetchResult<>(introduce));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "updateIntroduce,onFailed:" + errorCode);
            introduceData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 更新群昵称
   *
   * @param teamId 群ID
   * @param nickname 我的群昵称
   */
  public void updateNickname(String teamId, String nickname) {
    ALog.d(LIB_TAG, TAG, "updateNickname:" + teamId);
    TeamRepo.updateMemberNick(
        teamId,
        Objects.requireNonNull(IMKitClient.account()),
        nickname,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "updateNickname,onSuccess");
            nicknameData.setValue(new FetchResult<>(nickname));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "updateNickname,onFailed:" + errorCode);
            nicknameData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 更新群头像
   *
   * @param teamId 群ID
   * @param iconUrl 群头像URL
   */
  public void updateIcon(String teamId, String iconUrl) {
    ALog.d(LIB_TAG, TAG, "updateIcon:" + teamId);
    TeamRepo.updateTeamIcon(
        teamId,
        iconUrl,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "updateIcon,onSuccess");
            iconData.setValue(new FetchResult<>(iconUrl));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "updateIcon,onFailed:" + errorCode);
            iconData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 退出群
   *
   * @param team 群信息
   */
  public void quitTeam(V2NIMTeam team) {
    ALog.d(LIB_TAG, TAG, "quitTeam:" + team.getTeamId());
    if (TextUtils.equals(team.getOwnerAccountId(), IMKitClient.account())) {
      creatorQuitTeam(team.getTeamId());
    } else {
      quitTeam(team.getTeamId());
    }
  }

  /**
   * 群主退群，需要将群转让给他人
   *
   * @param teamId 群ID
   */
  public void creatorQuitTeam(String teamId) {
    ALog.d(LIB_TAG, TAG, "creatorQuitTeam:" + teamId);
    if (TextUtils.isEmpty(teamId)) {
      return;
    }

    TeamRepo.getTeamMemberListWithUserInfo(
        teamId,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable TeamMemberListResult param) {
            String account = IMKitClient.account();
            if (param == null
                || param.getMemberList() == null
                || param.getMemberList().size() <= 1) {
              dismissTeam(teamId);
              return;
            }
            for (TeamMemberWithUserInfo user : param.getMemberList()) {
              if (user != null && !TextUtils.equals(account, user.getAccountId())) {
                account = user.getAccountId();
                break;
              }
            }
            if (!TextUtils.isEmpty(account)) {
              TeamRepo.transferTeam(
                  teamId,
                  account,
                  true,
                  new FetchCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void param) {
                      ALog.d(LIB_TAG, TAG, "creatorQuitTeam transferTeam,onSuccess");
                      EventCenter.notifyEvent(
                          new TeamEvent(teamId, TeamEventAction.ACTION_DISMISS));
                      quitTeamData.setValue(new FetchResult<>(param));
                    }

                    @Override
                    public void onError(int errorCode, String errorMsg) {
                      ALog.d(LIB_TAG, TAG, "creatorQuitTeam transferTeam,onFailed:" + errorCode);
                      quitTeamData.setValue(new FetchResult<>(errorCode, errorMsg));
                    }
                  });
            }
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "creatorQuitTeam,onFailed:" + errorCode);
            quitTeamData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 退出群
   *
   * @param teamId 群ID
   */
  public void quitTeam(String teamId) {
    ALog.d(LIB_TAG, TAG, "quitTeam,teamId:" + teamId);
    TeamRepo.leaveTeam(
        teamId,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "quitTeam,onSuccess");
            EventCenter.notifyEvent(new TeamEvent(teamId, TeamEventAction.ACTION_DISMISS));
            quitTeamData.setValue(new FetchResult<>(param));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "quitTeam,onFailed:" + errorCode);
            if (errorCode == ChatKitConstant.ERROR_CODE_QUIT_TEAM_NO_MEMBER) {
              dismissTeam(teamId);
              return;
            }
            quitTeamData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 解散群
   *
   * @param teamId 群ID
   */
  public void dismissTeam(String teamId) {
    ALog.d(LIB_TAG, TAG, "dismissTeam:" + teamId);
    EventCenter.notifyEvent(new TeamEvent(teamId, TeamEventAction.ACTION_DISMISS));
    TeamRepo.dismissTeam(
        teamId,
        new FetchCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "dismissTeam,onSuccess");
            dismissTeamData.setValue(new FetchResult<>(param));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "dismissTeam,onFailed:" + errorCode);
            dismissTeamData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 群消息提醒
   *
   * @param teamId 群ID
   * @param notify 是否提醒
   */
  public void setTeamNotify(String teamId, boolean notify) {
    ALog.d(LIB_TAG, TAG, "setTeamNotify:" + teamId + "," + notify);
    V2NIMTeamMessageMuteMode muteMode =
        notify
            ? V2NIMTeamMessageMuteMode.V2NIM_TEAM_MESSAGE_MUTE_MODE_OFF
            : V2NIMTeamMessageMuteMode.V2NIM_TEAM_MESSAGE_MUTE_MODE_ON;
    TeamRepo.setTeamMuteStatus(
        teamId,
        V2NIMTeamType.V2NIM_TEAM_TYPE_NORMAL,
        muteMode,
        new FetchCallback<>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "setTeamNotify,onSuccess");
            notifyData.setValue(new FetchResult<>(notify));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "setTeamNotify,onFailed:" + errorCode);
            notifyData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }

  /**
   * 置顶
   *
   * @param sessionId 会话ID
   * @param stick 是否置顶
   */
  public void stickTop(String sessionId, boolean stick) {
    ALog.d(LIB_TAG, TAG, "configStick:" + sessionId + "," + stick);
    if (TextUtils.isEmpty(sessionId)) {
      stickData.setValue(new FetchResult<>(-1, ""));
      return;
    }
    ConversationRepo.stickTop(
        V2NIMConversationIdUtil.teamConversationId(teamId),
        stick,
        new FetchCallback<Void>() {
          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "stickTop,onFailed:" + errorCode);
            stickData.setValue(new FetchResult<>(!stick));
          }

          @Override
          public void onSuccess(@Nullable Void data) {
            ALog.d(LIB_TAG, TAG, "configStick,onSuccess:" + stick);
            stickData.setValue(new FetchResult<>(stick));
          }
        });
  }

  /**
   * 禁言
   *
   * @param teamId 群ID
   * @param mute 是否禁言
   */
  public void muteTeamAllMember(String teamId, boolean mute) {
    ALog.d(LIB_TAG, TAG, "muteTeamAllMember:" + teamId + "," + mute);
    TeamRepo.muteAllMembers(
        teamId,
        mute,
        new FetchCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void param) {
            ALog.d(LIB_TAG, TAG, "muteTeamAllMember,onSuccess");
            muteTeamAllMemberData.setValue(new FetchResult<>(mute));
          }

          @Override
          public void onError(int errorCode, String errorMsg) {
            ALog.d(LIB_TAG, TAG, "muteTeamAllMember,onFailed:" + errorCode);
            muteTeamAllMemberData.setValue(new FetchResult<>(errorCode, errorMsg));
          }
        });
  }
}
