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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.io.payloads.ByteSourcePayload;
import org.jclouds.sshj.SshjSshClient;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.common.io.Files;


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

	/**
	 * Initializes the ConfigUtil with the XDEnvironment.
	 *
	 * @param xdEnvironment
	 */
	public ConfigUtil(XdEnvironment xdEnvironment) {
		Assert.notNull(xdEnvironment, "XdEnvironment can not be null");
		this.xdEnvironment = xdEnvironment;
	}

	/**
	 * This method retrieves the information from the artifact and returns it as Properties.
	 *
	 * @return a properties entry that contains the IP of the XD admin and containers.
	 */
	public static Properties getPropertiesFromArtifact() {
		Properties props = new Properties();
		BufferedReader reader;
		String containerHosts = null;
		try {
			final File file = new File(ARTIFACT_NAME);
			if (file.exists()) {
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
				reader.close();
			}
		}
		catch (IOException ioe) {
			// Ignore file open error. Default to System variables.
		}

		if (containerHosts != null) {
			props.put(XdEnvironment.XD_CONTAINERS, containerHosts);
		}

		return props;
	}

	/**
	 * Retrieves the private key from a file, so we can execute commands on the container.
	 *
	 * @param privateKeyFileName The location of the private key file
	 * @return private key
	 */
	public static String getPrivateKey(String privateKeyFileName) {
		Assert.hasText(privateKeyFileName, "privateKeyFileName must not be empty nor null");
		try {
			isFilePresent(privateKeyFileName);
			String result = "";
			BufferedReader br = new BufferedReader(new FileReader(privateKeyFileName));
			while (br.ready()) {
				result += br.readLine() + "\n";
			}
			br.close();
			return result;
		}
		catch (IOException ioException) {
			throw new IllegalStateException(ioException.getMessage());
		}
	}

	private void sshCopy(File file, String fileName, String host) throws IOException {
		final LoginCredentials credential = LoginCredentials
				.fromCredentials(new Credentials("ubuntu", xdEnvironment.getPrivateKey()));
		final com.google.common.net.HostAndPort socket = com.google.common.net.HostAndPort
				.fromParts(host, 22);
		final SshjSshClient client = new SshjSshClient(
				new BackoffLimitedRetryHandler(), socket, credential, 5000);
		final ByteSourcePayload payload = new ByteSourcePayload(Files.asByteSource(file));
		client.put(xdEnvironment.getBaseDir() + "/config/" + fileName + ".properties", payload);
	}

	private static void isFilePresent(String keyFile) {
		File file = new File(keyFile);
		if (!file.exists()) {
			throw new IllegalArgumentException("The XD Private Key File ==> " + keyFile + " does not exist.");
		}
	}

}
