/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.restserver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.ClientInfo;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.service.StatusService;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.*;

import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class RestApiServer implements IFloodlightModule, IRestApiService {
	protected static Logger logger = LoggerFactory.getLogger(RestApiServer.class);
	protected List<RestletRoutable> restlets;
	protected FloodlightModuleContext fmlContext;
	protected String restHost = null;

	private static String keyStorePassword;
	private static String keyStore;

	private static String httpsNeedClientAuth = "true";

	private static boolean useHttps = false;
	private static boolean useHttp = false;

	private static String httpsPort;
	private static String httpPort;
	

	public static SQLiteConnection db;
	public static SQLiteStatement st;


	// ***********
	// Application
	// ***********

	public class RestApplication extends Application {
		protected Context context;
		
		public RestApplication() {
			super(new Context());
			this.context = getContext();
		}

		@Override
		public Restlet createInboundRoot() {
			Router baseRouter = new Router(context);
			baseRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);
			for (RestletRoutable rr : restlets) {
				baseRouter.attach(rr.basePath(), rr.getRestlet(context));
			}

			Filter slashFilter = new Filter() {
				
				@Override
				protected int beforeHandle(Request request, Response response) {
					Reference ref = request.getResourceRef();
					ClientInfo c = request.getClientInfo();
					String address = c.getAddress(); 
					String name = c.getAgent();
					String originalPath = ref.getPath();
					if (originalPath.contains("//"))
					{
						String newPath = originalPath.replaceAll("/+", "/");
						ref.setPath(newPath);
					}
					
					if(Math.random() < 1.1) { // Chance of 0.1 (sample rate of 10%) of storing the API access in the log.
						try {
							//Note: It is extremely inefficient to open the database connection every time.
							
							st.bind(1, address);
							st.bind(2, name);
							st.bind(3, ref.getPath());
							st.bind(4, new Date().getTime());
							st.step();
							st.dispose();
							db.dispose();
						} catch(SQLiteException e) {
							logger.info("SQLite Exception:" + e.getMessage());	
						}
						logger.info("[RESTSERVER] path: " + ref.getPath() + ", from: " + address + " - " + name);
					}
					return Filter.CONTINUE;
				}

			};
			slashFilter.setNext(baseRouter);

			return slashFilter;
		}

		public void run(FloodlightModuleContext fmlContext, String restHost) {
			setStatusService(new StatusService() {
				@Override
				public Representation getRepresentation(Status status,
						Request request,
						Response response) {
					return new JacksonRepresentation<Status>(status);
				}                
			});

			// Add everything in the module context to the rest
			for (Class<? extends IFloodlightService> s : fmlContext.getAllServices()) {
				if (logger.isTraceEnabled()) {
					logger.trace("Adding {} for service {} into context",
							s.getCanonicalName(), fmlContext.getServiceImpl(s));
				}
				context.getAttributes().put(s.getCanonicalName(), 
						fmlContext.getServiceImpl(s));
			}

			/*
			 * Specifically add the FML for use by the REST API's /wm/core/modules/...
			 */
			context.getAttributes().put(fmlContext.getModuleLoader().getClass().getCanonicalName(), fmlContext.getModuleLoader());

			/* Start listening for REST requests */
			try {
				final Component component = new Component();

				if (RestApiServer.useHttps) {
					Server server;

					if (restHost == null) {
						server = component.getServers().add(Protocol.HTTPS, Integer.valueOf(RestApiServer.httpsPort));
					} else {
						server = component.getServers().add(Protocol.HTTPS, restHost, Integer.valueOf(RestApiServer.httpsPort));
					}

					Series<Parameter> parameters = server.getContext().getParameters();
					//parameters.add("sslContextFactory", "org.restlet.ext.jsslutils.PkixSslContextFactory");
					parameters.add("sslContextFactory", "org.restlet.engine.ssl.DefaultSslContextFactory");

					parameters.add("keystorePath", RestApiServer.keyStore);
					parameters.add("keystorePassword", RestApiServer.keyStorePassword);
					parameters.add("keyPassword", RestApiServer.keyStorePassword);
					parameters.add("keystoreType", "JKS");

					parameters.add("truststorePath", RestApiServer.keyStore);
					parameters.add("truststorePassword", RestApiServer.keyStorePassword);
					parameters.add("trustPassword", RestApiServer.keyStorePassword);
					parameters.add("truststoreType", "JKS");

					parameters.add("needClientAuthentication", RestApiServer.httpsNeedClientAuth);
				}

				if (RestApiServer.useHttp) {
					if (restHost == null) {
						component.getServers().add(Protocol.HTTP, Integer.valueOf(RestApiServer.httpPort));
					} else {
						component.getServers().add(Protocol.HTTP, restHost, Integer.valueOf(RestApiServer.httpPort));
					}
				}

				component.getClients().add(Protocol.CLAP);
				component.getDefaultHost().attach(this);
				component.start();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	// ***************
	// IRestApiService
	// ***************

	@Override
	public void addRestletRoutable(RestletRoutable routable) {
		restlets.add(routable);
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled()) {
			StringBuffer sb = new StringBuffer();
			sb.append("REST API routables: ");
			for (RestletRoutable routable : restlets) {
				sb.append(routable.getClass().getSimpleName());
				sb.append(" (");
				sb.append(routable.basePath());
				sb.append("), ");
			}
			logger.debug(sb.toString());
		}

		RestApiServer.db = new SQLiteConnection(new File("restlog.sqlite"));
		try{
			RestApiServer.db.open(true);
			RestApiServer.st = db.prepare("INSERT INTO restlog (ip, agent, api, timestamp) VALUES (?, ?, ?, ?)");
		}
		catch(SQLiteException e)
		{
			System.out.println("Unable to open restlog database.");
		}

		RestApplication restApp = new RestApplication();
		restApp.run(fmlContext, restHost);
	}

	// *****************
	// IFloodlightModule
	// *****************

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> services =
				new ArrayList<Class<? extends IFloodlightService>>(1);
		services.add(IRestApiService.class);
		return services;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m = 
		new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		m.put(IRestApiService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// We don't have any
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// This has to be done here since we don't know what order the
		// startUp methods will be called
		this.restlets = new ArrayList<RestletRoutable>();
		this.fmlContext = context;

		// read our config options
		Map<String, String> configOptions = context.getConfigParams(this);
		restHost = configOptions.get("host");
		if (restHost == null) {
			Map<String, String> providerConfigOptions = context.getConfigParams(
					FloodlightProvider.class);
			restHost = providerConfigOptions.get("openflowhost");
		}
		if (restHost != null) {
			logger.debug("REST host set to {}", restHost);
		}

		String path = configOptions.get("keyStorePath");
		String pass = configOptions.get("keyStorePassword");
		String useHttps = configOptions.get("useHttps");
		String useHttp = configOptions.get("useHttp");
		String httpsNeedClientAuth = configOptions.get("httpsNeedClientAuthentication");

		/* HTTPS Access (ciphertext) */
		if (useHttps == null || path == null || path.isEmpty() || 
				(!useHttps.trim().equalsIgnoreCase("yes") && !useHttps.trim().equalsIgnoreCase("true") &&
						!useHttps.trim().equalsIgnoreCase("yep") && !useHttps.trim().equalsIgnoreCase("ja") &&
						!useHttps.trim().equalsIgnoreCase("stimmt")
						)
				) {
			RestApiServer.useHttps = false;
			RestApiServer.keyStore = null;
			RestApiServer.keyStorePassword = null;
		} else {
			RestApiServer.useHttps = true;
			RestApiServer.keyStore = path;
			RestApiServer.keyStorePassword = (pass == null ? "" : pass);
			String port = configOptions.get("httpsPort");
			if (port != null && !port.isEmpty()) {
				RestApiServer.httpsPort = port.trim();
			}
			if (httpsNeedClientAuth == null || (!httpsNeedClientAuth.trim().equalsIgnoreCase("yes") &&
					!httpsNeedClientAuth.trim().equalsIgnoreCase("true") &&
					!httpsNeedClientAuth.trim().equalsIgnoreCase("yep") &&
					!httpsNeedClientAuth.trim().equalsIgnoreCase("ja") &&
					!httpsNeedClientAuth.trim().equalsIgnoreCase("stimmt"))
					) {
				RestApiServer.httpsNeedClientAuth = "false";
			} else {
				RestApiServer.httpsNeedClientAuth = "true";
			}
		}

		/* HTTP Access (plaintext) */
		if (useHttp == null || path == null || path.isEmpty() || 
				(!useHttp.trim().equalsIgnoreCase("yes") && !useHttp.trim().equalsIgnoreCase("true") &&
						!useHttp.trim().equalsIgnoreCase("yep") && !useHttp.trim().equalsIgnoreCase("ja") &&
						!useHttp.trim().equalsIgnoreCase("stimmt")
						)
				) {
			RestApiServer.useHttp = false;
		} else {
			RestApiServer.useHttp = true;
			String port = configOptions.get("httpPort");
			if (port != null && !port.isEmpty()) {
				RestApiServer.httpPort = port.trim();
			}	
		}
		
		if (RestApiServer.useHttp && RestApiServer.useHttps && RestApiServer.httpPort.equals(RestApiServer.httpsPort)) {
			logger.error("REST API's HTTP and HTTPS ports cannot be the same. Got " + RestApiServer.httpPort + " for both.");
			throw new IllegalArgumentException("REST API's HTTP and HTTPS ports cannot be the same. Got " + RestApiServer.httpPort + " for both.");
		}

		if (!RestApiServer.useHttps) {
			logger.warn("HTTPS disabled; HTTPS will not be used to connect to the REST API.");
		} else {
			if (RestApiServer.httpsNeedClientAuth.equals("true")) {
				logger.warn("HTTPS enabled; Only trusted clients permitted. Allowing secure access to REST API on port {}.", RestApiServer.httpsPort);
			} else {
				logger.warn("HTTPS enabled; All clients permitted. Allowing secure access to REST API on port {}.", RestApiServer.httpsPort);
			}
			logger.info("HTTPS' SSL keystore/truststore path: {}, password: {}", RestApiServer.keyStore, RestApiServer.keyStorePassword);
		}

		if (!RestApiServer.useHttp) {
			logger.warn("HTTP disabled; HTTP will not be used to connect to the REST API.");
		} else {
			logger.warn("HTTP enabled; Allowing unsecure access to REST API on port {}.", RestApiServer.httpPort);
		}
	}

	@Override
	public void startUp(FloodlightModuleContext Context) {
		// no-op
	}
}