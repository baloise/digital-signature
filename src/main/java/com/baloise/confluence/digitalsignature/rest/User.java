package com.baloise.confluence.digitalsignature.rest;
import javax.xml.bind.annotation.*;

@XmlRootElement
public class User
{
    @XmlElement
    private String fullname;

    @XmlElement
    private String key;

    // This private constructor isn't used by any code, but JAXB requires any
    // representation class to have a no-args constructor.
    private User() { }

    public User(String key, String fullname)
    {
        this.key = key;
        this.fullname = fullname;
    }

}