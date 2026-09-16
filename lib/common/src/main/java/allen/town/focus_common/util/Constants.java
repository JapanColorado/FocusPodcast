package allen.town.focus_common.util;

import java.util.Random;
import java.util.UUID;

/**
 * desc: 常量类
 * author: Administrator .
 * date: 2018/4/12 17:41 .
 */
public class Constants {

    public static final String GIF = ".gif";
    public static final String IMAGE_GIF = "image/gif";
    public static final float SUBSAMPLINGSCALEIMAGEVIEW_SCAN = 20.0f;
    public static final String DECODE = "UTF-8";
    public static final String PRODUCT_EMAIL = "products.focus@gmail.com";

    /**
     * 通话结束广播，开在可注册该广播接收相关通话信息
     */
    public static final String CALL_END_INFO_RECEIVER = "com.mye.yuntongxun.sdk.imsdk.callend";
    /**
     * 通话结束广播对应budle
     */
    public static final String BUNDLE_CALL_END = "bundle_call_end";
    /**
     * 勿扰模式全局设置拼接符
     */
    public static final String APP_SILENT_MODE = "app_silent_mode_";
    public static final String LOGIN_FAIL_MESSAGE = "login_fail_message";

    public static final String KEY_SDK_INIT_RESULT = "key_sdk_init_result";
    //分享相关功能发送时间
    public static final String KEY_CIRCLE_PUBLISH_PROCESSTIME = "key_circle_publish_processtime";
    public static final String KEY_CIRCLE_PUBLISH_COUNT = "key_circle_publish_count";
    public static final String KEY_CIRCLE_OLD_ID = "key_circle_old_id";
    public static final String KEY_CIRCLE_NEW_ID = "key_circle_new_id";
    public static final String KEY_NEW_COMMENT_ID = "key_new_comment_id";
    public static final String KEY_OLD_COMMENT_ID = "key_old_comment_id";
    public static final String RECEIVER_UPDATE_CIRCLE = "com.mye.yuntongxun.sdk.update_circle";
    public static final String RECEIVER_UPDATE_LOCAL_CIRCLE = "com.mye.yuntongxun.sdk.update_local_circle";
    public static final String KEY_CIRCLE_PUBLISH_ID = "key_circle_publish_id";
    public static final String KEY_LATEST_PHOTO = "key_latest_photo ";
    public static final String KEY_LOCAL_IMAGE = "https://localhost";
    public static final String ALLOW_3G_OR_4G_KEY = "allow_3g_or_4g";
    public static final String MY_EXPERT_DEBUG_MODE = "**8668**";// Added by
    public static final String LAST_REMOTE_CONTACTS_LIST_CHECK = "remote_contacts_check_date";

    public static final String USER_AGENT = "android";

    public static final String VIDEO_CALL_ACTION = "com.mye.yuntongxun.sdk.cancel.videocall";
    public static final String VIDEO_CALL_FROM = "from";
    public static final String VIDEO_CALL_CONTENT = "content";

    //added by txp, 2013-5-7
    public static final String NV_WIZARD_TAG = "NV";

    public static final String KEY_MESSAGE_PAGE_INDEX = "message_page_index";
    public static final String KEY_MESSAGE_PAGE_INNER_INDEX = "message_page_inner_index";
    //选择联系人
    public static final String KEY_SELECTED_CONTACTS = "selected_contacts";


    /**
     * 发审批、任务、日志时，给审批人发消息的内容的最大长度
     */
    public static final int MAX_MSG_CONTENT_LENGHT = 50;

    /**
     * startActivityForResult 常量
     */
    public static final int REQUEST_CODE_SHARE_TO = 1001;
    /**
     * 发工作或者发分享最大图片数
     */
    public static final int MAX_IMAGES_COUNT = 9;
    /**
     * 记录当前账号是否已经将旧版本的静音和置顶数据同步到服务端
     */
    public static final String KEY_SESSION_ATTRIBUTE_UPLOAD = "key_session_attribute_upload";
    /**
     * 记录当前账号是否已经成功获取过静音列表和置顶列表
     */
    public static final String KEY_SESSION_ATTRIBUTE = "key_session_attribute";
    /**
     * @所有人时的用户名
     */
    public static final String AT_ALL_USERNAME = "@all";
    /**
     * 清除通知栏指定用户通知
     */
    public static final String KEY_CANCEL_USERNAME = "key_cancel_username";

    /**
     * get uuid
     *
     * @return
     */
    public static String getMyUuid() {
        UUID uuid = UUID.randomUUID();
        String uniqueId = uuid.toString();
        return uniqueId;
    }

    /**
     * According to the current time , return a random int value
     *
     * @return
     */
    public static int getRandomInt() {
        Random ran = new Random(System.currentTimeMillis());
        int content = ran.nextInt();
        return content;
    }


}
