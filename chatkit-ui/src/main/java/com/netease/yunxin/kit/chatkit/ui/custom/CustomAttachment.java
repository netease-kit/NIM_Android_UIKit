// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.chatkit.ui.custom;

import com.netease.nimlib.sdk.msg.attachment.MsgAttachment;
import com.netease.yunxin.kit.chatkit.ui.R;
import com.netease.yunxin.kit.corekit.im.IMKitClient;
import com.netease.yunxin.kit.corekit.im.model.AttachmentContent;
import org.json.JSONObject;

public abstract class CustomAttachment implements MsgAttachment, AttachmentContent {

  protected int type;

  public CustomAttachment(int type) {
    this.type = type;
  }

  public void fromJson(JSONObject data) {
    if (data != null) {
      parseData(data);
    }
  }

  @Override
  public String toJson(boolean send) {
    return CustomAttachParser.packData(type, packData());
  }

  public int getType() {
    return type;
  }

  public String getContent() {
    return IMKitClient.getApplicationContext().getString(R.string.chat_reply_message_brief_custom);
  }

  protected abstract void parseData(JSONObject data);

  protected abstract JSONObject packData();
}
