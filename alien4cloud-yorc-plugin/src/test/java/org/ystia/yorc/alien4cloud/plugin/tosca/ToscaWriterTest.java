package org.ystia.yorc.alien4cloud.plugin.tosca;

import java.nio.file.Paths;
import java.util.Set;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingResult;
import com.google.common.collect.Lists;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.types.CapabilityType;
import org.alien4cloud.tosca.model.types.DataType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.types.RelationshipType;
import org.alien4cloud.tosca.normative.constants.NormativeCredentialConstant;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * A {@code ToscaSerializationTest} is a ...
 *
 * @author Loic Albertin
 */
public class ToscaWriterTest extends AbstractToscaParserTest {

    @Override
    protected String getRootDirectory() {
        return "src/test/resources/org/ystia/yorc/alien4cloud/plugin/tosca";
    }

    @Override
    protected String getToscaVersion() {
        return "alien_dsl_2_0_0";
    }


    @Test
    public void testComponentSerialization() throws Exception {
        Mockito.reset(csarRepositorySearchService);

        Csar csar = new Csar("tosca-normative-types", "1.0.0-ALIEN14");
        Mockito.when(csarRepositorySearchService.getArchive(csar.getName(), csar.getVersion())).thenReturn(csar);
        NodeType mockedResult = Mockito.mock(NodeType.class);
        Mockito.when(csarRepositorySearchService
                .getElementInDependencies(Mockito.eq(NodeType.class), Mockito.eq("tosca.nodes.SoftwareComponent"),
                        Mockito.any(Set.class))).thenReturn(mockedResult);
        Mockito.when(mockedResult.getDerivedFrom()).thenReturn(Lists.newArrayList("tosca.nodes.Root"));
        Mockito.when(csarRepositorySearchService
                .getElementInDependencies(Mockito.eq(NodeType.class), Mockito.eq("tosca.nodes.Root"), Mockito.any(Set.class)))
                .thenReturn(mockedResult);

        Mockito.when(csarRepositorySearchService
                        .getElementInDependencies(Mockito.eq(NodeType.class), Mockito.eq("tosca.nodes.Compute"), Mockito.any(Set.class)))
                .thenReturn(mockedResult);
        Mockito.when(csarRepositorySearchService
                        .getElementInDependencies(Mockito.eq(DataType.class), Mockito.eq(NormativeCredentialConstant.DATA_TYPE), Mockito.any(Set.class)))
                .thenReturn(Mockito.mock(DataType.class));

        RelationshipType hostedOn = new RelationshipType();
        Mockito.when(csarRepositorySearchService
                .getElementInDependencies(Mockito.eq(RelationshipType.class), Mockito.eq("tosca.relationships.HostedOn"),
                        Mockito.any(Set.class))).thenReturn(hostedOn);


        ParsingResult<ArchiveRoot>
                parsingResult = parser.parseFile(Paths.get(getRootDirectory(), "tosca_component_input.yaml"));
        System.out.println(parsingResult.getContext().getParsingErrors());
        assertNoBlocker(parsingResult);

        String resultYaml = toscaComponentExporter.getYaml(parsingResult.getResult());
        System.out.println(resultYaml);
        String expectedResult = FileUtils.readFileToString(Paths.get(getRootDirectory(), "tosca_component_output.yaml").toFile());
        // Make some whitespaces change here as IDEs have auto-format features that will overwrite them in the file
        expectedResult = expectedResult.replaceAll("verbose:\\n", "verbose: \n");

        Assert.assertEquals(expectedResult, resultYaml);
    }
}
