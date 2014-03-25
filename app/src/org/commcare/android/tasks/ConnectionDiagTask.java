package org.commcare.android.tasks;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.locale.Localization;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * @author srengesh
 *
 */

public abstract class ConnectionDiagTask<R> extends CommCareTask<Void, String, String, R>
{	
	Context c;
	CommCarePlatform platform;
	
	public static final int CONNECTION_ID = 12335800;
	
	//strings used to in various diagnostics tests. Change these values if the URLs/HTML code is changed.
	public static final String googleURL = "www.google.com";
	public static final String commcareURL = "http://www.commcarehq.org/serverup.txt";
	public static final String commcareHTML = "success";
	public static final String pingCommand = "ping -c 1 ";
	
	public ConnectionDiagTask(Context c, CommCarePlatform platform) throws SessionUnavailableException
	{
		this.c = c;
		this.platform = platform;
		this.taskId = this.CONNECTION_ID;
	}
	
	@Override
	protected void onProgressUpdate(String... values) 
	{
		super.onProgressUpdate(values);
	}
	
	@Override
	protected void onPostExecute(String result) 
	{
		System.out.println(result);
		super.onPostExecute(result);
	}
	
	@Override
	protected void onCancelled() 
	{
		super.onCancelled();
	}
	
	@Override
	protected String doTaskBackground(Void... params) 
	{	
		if(!isOnline(this.c))
			return Localization.get("connection.task.internet.fail");
		if(!pingSuccess(googleURL))
			return Localization.get("connection.task.remote.ping.fail");
		try 
		{
			if(!pingCC(commcareURL).equals(commcareHTML))
				return Localization.get("connection.task.commcare.html.fail");
		} 
		catch (ClientProtocolException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		return Localization.get("connection.task.success");
	}
	
	//checks if the network is connected or not.
	private static boolean isOnline(Context context) {
	      ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	      NetworkInfo netInfo = conManager.getActiveNetworkInfo();
	      return (netInfo != null && netInfo.isConnected());
	}
	
	//check if a ping to a specific ip address is successful.
	private static boolean pingSuccess(String url)
	{
		Process proc = null;
		try {
			//append the input url to the ping command
			StringBuilder a = new StringBuilder(pingCommand);
			a.append(url);
			String inp = a.toString();
			
			//run the ping command at runtime
			proc = java.lang.Runtime.getRuntime().exec(inp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		int out = Integer.MAX_VALUE;
		try {
			out = proc.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (NullPointerException e) {
			e.printStackTrace();
			return false;
		}
		//0 if success, 2 if fail
		return out == 0;
	}
	
	private static String pingCC(String url) throws ClientProtocolException, IOException
	{
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		InputStream stream = client.execute(get).getEntity().getContent();
		InputStreamReader reader = new InputStreamReader(stream);
		BufferedReader buff = new BufferedReader(reader);
		//should read "success" if the server is up.
		String out = buff.readLine();
		buff.close();
		reader.close();
		stream.close();
		return out;
	}
}
