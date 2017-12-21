import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.ui.browser.*

import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference

def json = '{{json}}'
def mapper = new ObjectMapper()
def project = Application.getInstance().getProject()
def map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {})
map.each{ key, array ->
    def tree = project.getBrowser().addSearchResultsTree()
    def elements = new ArrayList<>()
    array.each { id ->

        def element = Converters.getIdToElementConverter().apply(id, project)
        if (element == null) {
            return
        }
        elements.add(element)
    }
    tree.addFoundElements(elements)
}