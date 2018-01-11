package alien4cloud.plugin.Janus;


import javax.annotation.Resource;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.plugin.Janus.service.ToscaComponentExporter;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.tosca.parser.impl.ErrorCode;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.hateoas.HypermediaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * Test tosca parsing for Tosca Simple profile in YAML wd03
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("AbstractToscaParserTest")
public abstract class AbstractToscaParserTest {

//    @Profile("AbstractToscaParserTest")
    @Configuration
    @EnableAutoConfiguration(exclude = {HypermediaAutoConfiguration.class})
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @ComponentScan(basePackages = {"alien4cloud.plugin.Janus.service", "alien4cloud.tosca.context", "alien4cloud.tosca.parser",
            "alien4cloud.paas.wf"},
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = alien4cloud.plugin.Janus.service
                            .PluginArchiveService.class)})
    static class ContextConfiguration {
        @Bean
        public ICSARRepositorySearchService repositorySearchService() {
            return Mockito.mock(ICSARRepositorySearchService.class);
        }
    }

    protected abstract String getRootDirectory();

    protected abstract String getToscaVersion();

    @Resource
    protected ToscaParser parser;

    @Resource
    protected ICSARRepositorySearchService csarRepositorySearchService;

    @Resource
    protected ToscaComponentExporter toscaComponentExporter;


    public static int countErrorByLevelAndCode(ParsingResult<?> parsingResult, ParsingErrorLevel errorLevel, ErrorCode errorCode) {
        int finalCount = 0;
        for (int i = 0; i < parsingResult.getContext().getParsingErrors().size(); i++) {
            ParsingError error = parsingResult.getContext().getParsingErrors().get(i);
            if (error.getErrorLevel().equals(errorLevel) && (error.getErrorCode().equals(errorCode) || errorCode == null)) {
                finalCount++;
            }
        }
        return finalCount;
    }

    public static void assertNoBlocker(ParsingResult<?> parsingResult) {
        Assert.assertFalse(countErrorByLevelAndCode(parsingResult, ParsingErrorLevel.ERROR, null) > 0);
    }

}
