package org.ystia.yorc.alien4cloud.plugin;


import alien4cloud.component.ICSARRepositorySearchService;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration;
import org.springframework.boot.autoconfigure.hateoas.HypermediaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * Test tosca parsing for Tosca Simple profile in YAML wd03
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public abstract class AbstractPluginTest {

    //    @Profile("AbstractToscaParserTest")
    @Configuration
    @EnableAutoConfiguration(exclude = {HypermediaAutoConfiguration.class, SpringApplicationAdminJmxAutoConfiguration.class})
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @ComponentScan(basePackages = {"org.ystia.yorc.alien4cloud.plugin.service", "alien4cloud.tosca.context", "alien4cloud.tosca.parser",
            "alien4cloud.paas.wf"},
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = org.ystia.yorc.alien4cloud.plugin.service
                            .PluginArchiveService.class)})
    static class ContextConfiguration {
        @Bean
        public ICSARRepositorySearchService repositorySearchService() {
            return Mockito.mock(ICSARRepositorySearchService.class);
        }

    }
}
