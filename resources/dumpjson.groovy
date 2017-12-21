import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import java.nio.file.*
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters
import com.fasterxml.jackson.databind.ObjectMapper

path = Paths.get(System.getProperty("user.dir"), "json")
Files.createDirectories(path)
mapper = new ObjectMapper()
project = Application.getInstance().getProject()

void processElementsRecursively(Element element, Path path) {
    jsonObject = Converters.getElementToJsonConverter().apply(element, project)
    if (jsonObject == null) {
        return
    }
    file = path.resolve(Converters.getElementToIdConverter().apply(element) + ".json")
    Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonObject))
    for (Element ownedElement : element.getOwnedElement()) {
        processElementsRecursively(ownedElement, path)
    }
}
processElementsRecursively(project.getPrimaryModel(), path)