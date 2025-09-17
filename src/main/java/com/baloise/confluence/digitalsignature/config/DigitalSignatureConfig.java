package com.baloise.confluence.digitalsignature.config;

import static com.atlassian.plugins.osgi.javaconfig.OsgiServices.importOsgiService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.mail.server.MailServerManager;
import com.atlassian.mywork.service.LocalNotificationService;
import com.atlassian.plugins.osgi.javaconfig.configs.beans.ModuleFactoryBean;
import com.atlassian.plugins.osgi.javaconfig.configs.beans.PluginAccessorBean;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.user.GroupManager;

@Configuration
@Import({
        ModuleFactoryBean.class,
        PluginAccessorBean.class
})
public class DigitalSignatureConfig {

    @Bean
    public BandanaManager bandanaManager() {
        return importOsgiService(BandanaManager.class);
    }

    @Bean
    public UserManager userManager() {
        return importOsgiService(UserManager.class);
    }

    @Bean
    public BootstrapManager bootstrapManager() {
        return importOsgiService(BootstrapManager.class);
    }

    @Bean
    public PageManager pageManager() {
        return importOsgiService(PageManager.class);
    }

    @Bean
    public PermissionManager permissionManager() {
        return importOsgiService(PermissionManager.class);
    }

    @Bean
    public GroupManager groupManager() {
        return importOsgiService(GroupManager.class);
    }

    @Bean
    public I18nResolver i18nResolver() {
        return importOsgiService(I18nResolver.class);
    }

    @Bean
    public SettingsManager settingsManager() {
        return importOsgiService(SettingsManager.class);
    }

    @Bean
    public LocalNotificationService localNotificationService() {
        return importOsgiService(LocalNotificationService.class);
    }

    @Bean
    public MailServerManager mailServerManager() {
        return importOsgiService(MailServerManager.class);
    }
}