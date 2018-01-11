package alien4cloud.plugin.Janus;

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
        return "src/test/resources/alien4cloud/plugin/Janus";
    }

    @Override
    protected String getToscaVersion() {
        return "alien_dsl_1_4_0";
    }


    @Test
    public void testComponentUpdateSnake() throws Exception {
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
//        CapabilityType mockedCapabilityResult = Mockito.mock(CapabilityType.class);
//        Mockito.when(csarRepositorySearchService.getElementInDependencies(Mockito.eq(CapabilityType.class),
//                Mockito.eq("tosca.capabilities.Root"), Mockito.any(Set.class))).thenReturn(mockedCapabilityResult);
//
//        Mockito.when(csarRepositorySearchService
//                .getElementInDependencies(Mockito.eq(CapabilityType.class), Mockito.eq("tosca.capabilities.Endpoint"),
//                        Mockito.any(Set.class))).thenReturn(mockedCapabilityResult);
        RelationshipType hostedOn = new RelationshipType();
        Mockito.when(csarRepositorySearchService
                .getElementInDependencies(Mockito.eq(RelationshipType.class), Mockito.eq("tosca.relationships.HostedOn"),
                        Mockito.any(Set.class))).thenReturn(hostedOn);


        ParsingResult<ArchiveRoot>
                parsingResult = parser.parseFile(Paths.get(getRootDirectory(), "tosca_component_input.yaml"));
        System.out.println(parsingResult.getContext().getParsingErrors());
        Assert.assertEquals(0, parsingResult.getContext().getParsingErrors().size());


        System.out.println(toscaComponentExporter.getYaml(parsingResult.getResult()));
    }
}
