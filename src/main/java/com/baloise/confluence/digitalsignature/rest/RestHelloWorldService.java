package com.baloise.confluence.digitalsignature.rest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;

@Path("/")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class RestHelloWorldService {
    @GET
    @Path("currentUser")
    public Response getUncompletedUsers() {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
		return Response.ok(new User(user.getKey().getStringValue(), user.getFullName())).build();
    }
}