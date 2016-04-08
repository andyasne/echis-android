package org.commcare.network;

import org.commcare.logging.AndroidLogger;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequest {
    private final PasswordAuthentication passwordAuthentication;

    public ModernHttpRequest(String username, String password) {
        passwordAuthentication =
                new PasswordAuthentication(HttpRequestGenerator.buildDomainUser(username),
                        password.toCharArray());
    }

    public InputStream makeModernRequest(URL url) throws IOException {
        if (passwordAuthentication != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return passwordAuthentication;
                }
            });
        }

        int responseCode = -1;
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        setupGetConnection(con);
        con.connect();
        try {
            responseCode = con.getResponseCode();

            return followRedirect(con).getInputStream();
        } catch (IOException e) {
            if (e.getMessage().toLowerCase().contains("authentication") || responseCode == 401) {
                //Android http libraries _suuuuuck_, let's try apache.
                return null;
            } else {
                throw e;
            }
        }
    }

    private static HttpURLConnection followRedirect(HttpURLConnection httpConnection) throws IOException {
        final URL url = httpConnection.getURL();
        if (httpConnection.getResponseCode() == 301) {
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Attempting 1 stage redirect from " + url.toString() + " to " + httpConnection.getURL().toString());
            //only allow one level of redirection here for now.
            URL newUrl = new URL(httpConnection.getHeaderField("Location"));
            httpConnection.disconnect();
            httpConnection = (HttpURLConnection)newUrl.openConnection();
            setupGetConnection(httpConnection);
            httpConnection.connect();

            //Don't allow redirects _from_ https _to_ https unless they are redirecting to the same server.
            if (!HttpRequestGenerator.isValidRedirect(url, httpConnection.getURL())) {
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Invalid redirect from " + url.toString() + " to " + httpConnection.getURL().toString());
                throw new IOException("Invalid redirect from secure server to insecure server");
            }
        }

        return httpConnection;
    }

    private static void setupGetConnection(HttpURLConnection con) throws IOException {
        con.setConnectTimeout(GlobalConstants.CONNECTION_TIMEOUT);
        con.setReadTimeout(GlobalConstants.CONNECTION_SO_TIMEOUT);
        con.setRequestMethod("GET");
        con.setDoInput(true);
        con.setInstanceFollowRedirects(true);
    }
}
