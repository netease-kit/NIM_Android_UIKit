// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.conversationkit.ui.normal.page;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.netease.yunxin.kit.common.ui.viewholder.BaseViewHolder;
import com.netease.yunxin.kit.conversationkit.ui.common.ConversationConstant;
import com.netease.yunxin.kit.conversationkit.ui.databinding.SelectorViewHolderLayoutBinding;
import com.netease.yunxin.kit.conversationkit.ui.model.ConversationBean;
import com.netease.yunxin.kit.conversationkit.ui.normal.ViewHolderFactory;
import com.netease.yunxin.kit.conversationkit.ui.normal.viewholder.SelectorViewHolder;

/** 会话选选择器对应的ViewHolder工厂 */
class SelectorViewHolderFactory extends ViewHolderFactory {

  @Override
  public BaseViewHolder<ConversationBean> createViewHolder(
      @NonNull ViewGroup parent, int viewType) {

    if (viewType == ConversationConstant.ViewType.CHAT_VIEW
        || viewType == ConversationConstant.ViewType.TEAM_VIEW) {
      return new SelectorViewHolder(
          SelectorViewHolderLayoutBinding.inflate(
              LayoutInflater.from(parent.getContext()), parent, false));
    }
    return null;
  }
}
