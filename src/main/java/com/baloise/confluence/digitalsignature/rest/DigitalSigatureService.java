package com.baloise.confluence.digitalsignature.rest;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static java.net.URI.create;

import java.net.URI;
import java.util.Date;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.baloise.confluence.digitalsignature.Signature;

@Path("/")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Scanned
public class DigitalSigatureService {
	private BandanaManager bandanaManager;
	private SettingsManager settingsManager;
	
	public DigitalSigatureService(@ComponentImport BandanaManager bandanaManager, @ComponentImport SettingsManager settingsManager) {
		this.settingsManager = settingsManager;
		this.bandanaManager = bandanaManager;
	}

	@GET
    @Path("currentUser")
    public Response currentUser() {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
		return Response.ok(new User(user.getName(), bandanaManager.toString())).build();
    }
	
	@GET
	@Path("sign")
	public Response sign(@QueryParam("key") final String key) {
		ConfluenceUser user = AuthenticatedUserThreadLocal.get();
		Signature signature =  (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
		signature.getSignatures().put(user.getName(), new Date());
		bandanaManager.setValue(GLOBAL_CONTEXT, key, signature);
		URI pageUri = create(settingsManager.getGlobalSettings().getBaseUrl()+ "/pages/viewpage.action?pageId="+signature.getPageId());
		return Response.temporaryRedirect(pageUri).build();
	}
	
}