// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.teamkit.ui.activity;

import static com.netease.yunxin.kit.corekit.im2.utils.RouterConstant.KEY_TEAM_ID;
import static com.netease.yunxin.kit.teamkit.ui.dialog.BaseTeamIdentifyDialog.TYPE_TEAM_ALL_MEMBER;
import static com.netease.yunxin.kit.teamkit.ui.utils.NetworkUtilsWrapper.handleNetworkBrokenResult;
import static com.netease.yunxin.kit.teamkit.ui.utils.TeamUIKitConstant.TYPE_EXTENSION_VALUE_ALL;
import static com.netease.yunxin.kit.teamkit.ui.utils.TeamUIKitConstant.TYPE_EXTENSION_VALUE_MANAGER;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.netease.nimlib.sdk.v2.team.enums.V2NIMTeamInviteMode;
import com.netease.nimlib.sdk.v2.team.enums.V2NIMTeamUpdateInfoMode;
import com.netease.nimlib.sdk.v2.team.model.V2NIMTeam;
import com.netease.nimlib.sdk.v2.team.model.V2NIMTeamMember;
import com.netease.yunxin.kit.chatkit.model.TeamWithCurrentMember;
import com.netease.yunxin.kit.common.ui.activities.BaseActivity;
import com.netease.yunxin.kit.common.ui.viewmodel.FetchResult;
import com.netease.yunxin.kit.corekit.im2.IMKitClient;
import com.netease.yunxin.kit.teamkit.ui.R;
import com.netease.yunxin.kit.teamkit.ui.dialog.BaseTeamIdentifyDialog;
import com.netease.yunxin.kit.teamkit.ui.normal.dialog.TeamIdentifyDialog;
import com.netease.yunxin.kit.teamkit.ui.utils.TeamUtils;
import com.netease.yunxin.kit.teamkit.ui.viewmodel.TeamManagerViewModel;
import java.util.List;
import java.util.Objects;

/** 群管理页面基类 , 包含修改编辑权限、邀请权限、@所有人权限以及管理员管理入口 子类需要重写initViewAndGetRootView方法，返回对应的View */
public abstract class BaseTeamManagerActivity extends BaseActivity {
  protected TeamManagerViewModel viewModel;

  protected String teamId;
  protected V2NIMTeam teamInfo;
  protected V2NIMTeamMember teamMember;
  private View rootView;

  protected View backView;
  protected View viewEditManager;
  protected TextView tvManagerCount;
  protected View viewInvite;
  protected TextView tvInviteValue;

  protected View viewUpdate;

  protected TextView tvUpdateValue;

  protected View viewAit;

  protected TextView tvAitValue;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    rootView = initViewAndGetRootView(savedInstanceState);
    setContentView(rootView);
    changeStatusBarColor(R.color.color_eff1f4);
    initData();
  }

  protected abstract View initViewAndGetRootView(Bundle savedInstanceState);

  protected void checkViews() {
    Objects.requireNonNull(viewEditManager);
    Objects.requireNonNull(tvManagerCount);
    Objects.requireNonNull(viewInvite);
    Objects.requireNonNull(tvInviteValue);
    Objects.requireNonNull(viewUpdate);
    Objects.requireNonNull(tvUpdateValue);
    Objects.requireNonNull(viewAit);
    Objects.requireNonNull(tvAitValue);
  }

  protected void initData() {
    teamId = getIntent().getStringExtra(KEY_TEAM_ID);
    viewModel = new ViewModelProvider(this).get(TeamManagerViewModel.class);
    viewModel.configTeamId(teamId);
    viewModel.getManagerCountLiveData().observeForever(observerForMangerCount);
    viewModel.getUpdateInviteLiveData().observeForever(observerForInviteMode);
    viewModel.getUpdateTeamLiveData().observeForever(observerForTeamUpdate);
    viewModel.getUpdateAtLiveData().observeForever(observerForAt);
    viewModel.getTeamWitheMemberData().observeForever(observerForTeamData);
    viewModel.getTeamMemberUpdateData().observeForever(observerForTeamMemberUpdateData);
    viewModel.getTeamUpdateData().observeForever(observerForTeamUpdateData);
    viewModel.requestTeamData(teamId);
    viewModel.requestManagerCount(teamId);
  }

  protected void refreshUI(V2NIMTeam teamInfo) {
    tvInviteValue.setText(
        teamInfo.getInviteMode() == V2NIMTeamInviteMode.V2NIM_TEAM_INVITE_MODE_ALL
            ? R.string.team_all_member
            : R.string.team_owner_and_manager);
    viewInvite.setOnClickListener(
        v -> getTeamIdentifyDialog().show(type -> viewModel.updateInvitePrivilege(teamId, type)));

    tvUpdateValue.setText(
        teamInfo.getUpdateInfoMode() == V2NIMTeamUpdateInfoMode.V2NIM_TEAM_UPDATE_INFO_MODE_ALL
            ? R.string.team_all_member
            : R.string.team_owner_and_manager);
    viewUpdate.setOnClickListener(
        v -> getTeamIdentifyDialog().show(type -> viewModel.updateInfoPrivilege(teamId, type)));

    tvAitValue.setText(
        Objects.equals(TeamUtils.getTeamAtMode(teamInfo), TYPE_EXTENSION_VALUE_ALL)
            ? R.string.team_all_member
            : R.string.team_owner_and_manager);
    viewAit.setOnClickListener(
        v ->
            getTeamIdentifyDialog()
                .show(
                    type ->
                        viewModel.updateAtPrivilege(
                            teamInfo,
                            type == TYPE_TEAM_ALL_MEMBER
                                ? TYPE_EXTENSION_VALUE_ALL
                                : TYPE_EXTENSION_VALUE_MANAGER)));

    if (TextUtils.equals(IMKitClient.account(), teamInfo.getOwnerAccountId())) {
      viewEditManager.setVisibility(View.VISIBLE);
    } else {
      viewEditManager.setVisibility(View.GONE);
    }

    viewEditManager.setOnClickListener(v -> startTeamManagerListActivity());
    if (backView != null) {
      backView.setOnClickListener(v -> finish());
    }
  }

  // 跳转到管理员管理页面
  protected void startTeamManagerListActivity() {}

  @Override
  protected void onDestroy() {
    super.onDestroy();
    viewModel.getUpdateInviteLiveData().removeObserver(observerForInviteMode);
    viewModel.getUpdateTeamLiveData().removeObserver(observerForTeamUpdate);
    viewModel.getUpdateAtLiveData().removeObserver(observerForAt);
    viewModel.getTeamMemberUpdateData().observeForever(observerForTeamMemberUpdateData);
    viewModel.getTeamWitheMemberData().removeObserver(observerForTeamData);
    viewModel.getTeamUpdateData().removeObserver(observerForTeamUpdateData);
    viewModel.getManagerCountLiveData().removeObserver(observerForMangerCount);
  }

  // 获取设置权限设置对话框
  protected BaseTeamIdentifyDialog getTeamIdentifyDialog() {
    return new TeamIdentifyDialog(this);
  }

  // 群更新信息观察者
  private final Observer<FetchResult<V2NIMTeam>> observerForTeamUpdateData =
      teamResultInfo -> {
        dismissLoading();
        if (teamResultInfo.getData() == null || !teamResultInfo.isSuccess()) {
          finish();
          return;
        }
        teamInfo = teamResultInfo.getData();
        refreshUI(teamInfo);
      };
  // 群成员更新信息观察者, 用于更新群管理员人数
  private final Observer<FetchResult<List<V2NIMTeamMember>>> observerForTeamMemberUpdateData =
      teamResultInfo -> {
        dismissLoading();
        if (teamResultInfo.getData() == null || !teamResultInfo.isSuccess()) {
          finish();
          return;
        }
        viewModel.requestManagerCount(teamId);
      };
  // 当前用户群信息观察者
  private final Observer<FetchResult<TeamWithCurrentMember>> observerForTeamData =
      teamResultInfo -> {
        dismissLoading();
        if (teamResultInfo.getData() == null || !teamResultInfo.isSuccess()) {
          finish();
          return;
        }
        teamInfo = teamResultInfo.getData().getTeam();
        teamMember = teamResultInfo.getData().getTeamMember();
        refreshUI(teamInfo);
      };
  // 群@所有人权限观察者
  private final Observer<FetchResult<String>> observerForAt =
      stringResultInfo -> {
        if (!stringResultInfo.isSuccess() || stringResultInfo.getData() == null) {
          handleNetworkBrokenResult(this, stringResultInfo);
          return;
        }
        tvAitValue.setText(
            (Objects.equals(stringResultInfo.getData(), TYPE_EXTENSION_VALUE_ALL))
                ? R.string.team_all_member
                : R.string.team_owner_and_manager);
      };
  // 群信息编辑权限观察者
  private final Observer<FetchResult<Integer>> observerForTeamUpdate =
      resultInfo -> {
        if (!resultInfo.isSuccess() || resultInfo.getData() == null) {
          handleNetworkBrokenResult(this, resultInfo);
          return;
        }
        tvUpdateValue.setText(
            (resultInfo.getData() == V2NIMTeamInviteMode.V2NIM_TEAM_INVITE_MODE_ALL.getValue())
                ? R.string.team_all_member
                : R.string.team_owner_and_manager);
      };
  // 群邀请权限观察者
  private final Observer<FetchResult<Integer>> observerForInviteMode =
      resultInfo -> {
        if (!resultInfo.isSuccess() || resultInfo.getData() == null) {
          handleNetworkBrokenResult(this, resultInfo);
          return;
        }
        tvInviteValue.setText(
            (resultInfo.getData() == V2NIMTeamInviteMode.V2NIM_TEAM_INVITE_MODE_ALL.getValue())
                ? R.string.team_all_member
                : R.string.team_owner_and_manager);
      };

  // 群管理员数量
  private final Observer<FetchResult<Integer>> observerForMangerCount =
      resultInfo -> {
        if (!resultInfo.isSuccess() || resultInfo.getData() == null) {
          handleNetworkBrokenResult(this, resultInfo);
          return;
        }
        tvManagerCount.setText(String.valueOf(resultInfo.getData()));
      };
}
