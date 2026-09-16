package allen.town.focus_common.util;

import java.util.Random;
import java.util.UUID;

/**
 * desc: constants
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
     * Call-ended broadcast; register for it to receive call information.
     */
    public static final String CALL_END_INFO_RECEIVER = "com.mye.yuntongxun.sdk.imsdk.callend";
    /**
     * Bundle key of the call-ended broadcast.
     */
    public static final String BUNDLE_CALL_END = "bundle_call_end";
    /**
     * Prefix for the global do-not-disturb setting key.
     */
    public static final String APP_SILENT_MODE = "app_silent_mode_";
    public static final String LOGIN_FAIL_MESSAGE = "login_fail_message";

    public static final String KEY_SDK_INIT_RESULT = "key_sdk_init_result";
    // Send time for sharing features
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
    // Selected contacts
    public static final String KEY_SELECTED_CONTACTS = "selected_contacts";


    /**
     * Maximum length of the message sent to the approver when filing an approval, task or log.
     */
    public static final int MAX_MSG_CONTENT_LENGHT = 50;

    /**
     * startActivityForResult request codes.
     */
    public static final int REQUEST_CODE_SHARE_TO = 1001;
    /**
     * Maximum number of images in a work post or a share.
     */
    public static final int MAX_IMAGES_COUNT = 9;
    /**
     * Whether this account has already uploaded its legacy mute and pin data to the server.
     */
    public static final String KEY_SESSION_ATTRIBUTE_UPLOAD = "key_session_attribute_upload";
    /**
     * Whether this account has already fetched the mute list and the pin list.
     */
    public static final String KEY_SESSION_ATTRIBUTE = "key_session_attribute";
    /**
     * Username used for "@ everyone".
     */
    public static final String AT_ALL_USERNAME = "@all";
    /**
     * Clear the notifications of a given user from the status bar.
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
