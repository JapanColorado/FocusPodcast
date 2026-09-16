package allen.town.focus_common.http;


import androidx.annotation.Keep;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class SSLSocketClient {
    //获取这个SSLSocketFactory
    public static SSLSocketFactory getSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, getTrustManagers(), new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //获取TrustManager
    private static TrustManager[] getTrustManagers() {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManagerImpl()
        };
        return trustAllCerts;
    }

    /**
     * 需要实现IGsonEntity接口,否则android10查找的checkServerTrusted方法会不存在因为被混淆了,
     * 没有找到我们可以访问的X509ExtendedTrustManager实现了checkServerTrusted两个string参数的方案
     * 而com.sun.net.ssl.internal.ssl包下面的X509ExtendedTrustManager虽然实现了但是我们访问不了
     */
    @Keep
    static class X509TrustManagerImpl implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {

        }

        //android10会查找有没有这个方法必须加上,并且它不能被混淆否则系统通过反射找不到此方法
        public void checkServerTrusted(X509Certificate[] chain, String authType, String authType1) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }

    public static X509TrustManager getTrustManager() {
        X509TrustManager trustManager = (X509TrustManager) getTrustManagers()[0];
        return trustManager;
    }

    //获取HostnameVerifier
    public static HostnameVerifier getHostnameVerifier() {
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        };
        return hostnameVerifier;
    }
}
