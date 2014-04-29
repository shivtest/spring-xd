/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.integration.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Properties;

import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.sshj.SshjSshClient;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


/**
 * Tools to create and read configuration files for an environment deployed on EC2.
 * 
 * @author Glenn Renfro
 */
public class ConfigUtil {

	private static final int SERVER_TYPE_OFFSET = 0;

	private static final int HOST_OFFSET = 1;

	private static final int XD_PORT_OFFSET = 2;

	private static final int HTTP_PORT_OFFSET = 3;

	private static final int JMX_PORT_OFFSET = 4;

	// The artifacts file name
	private static final String ARTIFACT_NAME = "ec2servers.csv";


	// Each line in the artifact has a entry to identify it as admin, container or singlenode.
	// These entries represent the supported types.
	public static final String ADMIN_TOKEN = "adminNode";

	public static final String CONTAINER_TOKEN = "containerNode";

	public static final String SINGLENODE_TOKEN = "singleNode";

	private XdEnvironment xdEnvironment;


	public ConfigUtil(XdEnvironment xdEnvironment) {
		Assert.notNull(xdEnvironment, "XdEnvironment can not be null");
		this.xdEnvironment = xdEnvironment;
	}

	/**
	 * 
	 * Creates a properties file with the content you specify in the config directory of the host you specify.
	 * 
	 * @param fileName The name of the properties file.
	 * @param content The configuration data that should be put in the config.properties file.
	 * @throws IOException
	 */
	public void pushConfigToContainer(String fileName, String content) throws IOException {
		File conFile = new File("tmpConf.properties");
		conFile.deleteOnExit();
		BufferedWriter bw = new BufferedWriter(new FileWriter(conFile));
		bw.write(content);
		bw.close();
		if (!xdEnvironment.isOnEc2()) {
			String destination = xdEnvironment.getBaseDir() + "/config/" + fileName + ".properties";
			conFile.renameTo(new File(destination));
		}
		else {
			sshCopy(conFile, fileName, xdEnvironment.getDefaultTargetHost());
		}

	}

	@SuppressWarnings("deprecation")
	private void sshCopy(File file, String fileName, String host) throws IOException {
		final LoginCredentials credential = LoginCredentials
				.fromCredentials(new Credentials("ubuntu", xdEnvironment.getPrivateKey()));
		final com.google.common.net.HostAndPort socket = com.google.common.net.HostAndPort
				.fromParts(host, 22);
		final SshjSshClient client = new SshjSshClient(
				new BackoffLimitedRetryHandler(), socket, credential, 5000);
		final FilePayload payload = new FilePayload(file);
		client.put(xdEnvironment.getBaseDir() + "/config/" + fileName + ".properties", payload);
	}


	public static Properties getPropertiesFromArtifact() throws MalformedURLException {
		Properties props = null;
		BufferedReader reader = null;
		String containerHosts = null;
		try {
			final File file = new File(ARTIFACT_NAME);
			if (file.exists()) {
				props = new Properties();
				reader = new BufferedReader(new FileReader(ARTIFACT_NAME));
				while (reader.ready()) {
					final String line = reader.readLine();
					final String tokens[] = StringUtils
							.commaDelimitedListToStringArray(line);
					if (tokens.length < 4) {
						continue;// skip invalid lines
					}
					if (tokens[SERVER_TYPE_OFFSET].equals(ADMIN_TOKEN)) {
						props.setProperty(XdEnvironment.XD_ADMIN_HOST, XdEnvironment.HTTP_PREFIX
								+ tokens[HOST_OFFSET] + ":"
								+ tokens[XD_PORT_OFFSET]);
						props.setProperty(XdEnvironment.XD_HTTP_PORT,
								tokens[HTTP_PORT_OFFSET]);
						props.setProperty(XdEnvironment.XD_JMX_PORT, tokens[JMX_PORT_OFFSET]);

					}
					if (tokens[SERVER_TYPE_OFFSET].equals(CONTAINER_TOKEN)) {
						if (containerHosts == null) {
							containerHosts = XdEnvironment.HTTP_PREFIX
									+ tokens[HOST_OFFSET].trim() + ":"
									+ tokens[XD_PORT_OFFSET];
						}
						else {
							containerHosts = containerHosts + "," + XdEnvironment.HTTP_PREFIX
									+ tokens[HOST_OFFSET].trim() + ":"
									+ tokens[XD_PORT_OFFSET];
						}
					}
					if (tokens[SERVER_TYPE_OFFSET].equals(SINGLENODE_TOKEN)) {
						props.setProperty(XdEnvironment.XD_ADMIN_HOST, XdEnvironment.HTTP_PREFIX
								+ tokens[HOST_OFFSET] + ":"
								+ tokens[XD_PORT_OFFSET]);
						props.setProperty(XdEnvironment.XD_HTTP_PORT,
								tokens[HTTP_PORT_OFFSET]);
						props.setProperty(XdEnvironment.XD_JMX_PORT, tokens[JMX_PORT_OFFSET]);

						containerHosts = XdEnvironment.HTTP_PREFIX
								+ tokens[HOST_OFFSET].trim() + ":" + tokens[XD_PORT_OFFSET];
						props.put(XdEnvironment.XD_CONTAINERS, containerHosts);
					}
				}
			}
		}
		catch (IOException ioe) {
			// Ignore file open error. Default to System variables.
		}
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
			}
			catch (IOException ioe) {
				// ignore
			}
		}


		if (containerHosts != null) {
			props.put(XdEnvironment.XD_CONTAINERS, containerHosts);
		}


		return props;
	}


	private static void isFilePresent(String keyFile) {
		File file = new File(keyFile);
		if (!file.exists()) {
			throw new IllegalArgumentException("The XD Private Key File ==> " + keyFile + " does not exist.");
		}
	}

	/**
	 * retrieves the private key from a file, so we can execute commands on the container.
	 * 
	 * @param privateKeyFile The location of the private key file
	 * @return
	 * @throws IOException
	 */
	public static String getPrivateKey(String privateKeyFileName) throws IOException {
		isFilePresent(privateKeyFileName);
		String result = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(privateKeyFileName));
			while (br.ready()) {
				result += br.readLine() + "\n";
			}
		}
		finally {
			if (br != null) {
				try {
					br.close();
				}
				catch (Exception ex) {
					// ignore error.
				}
			}
		}
		return result;
	}

}
