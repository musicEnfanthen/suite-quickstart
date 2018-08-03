<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# Adding Resources

@@toc

To create a resource, the HTTP method `POST` has to be used.
The request has to be sent to the Knora server using the `resources`
path segment:

```
HTTP POST to http://host/v1/resources
```

Unlike in the case of GET requests, the request body consists of JSON
describing the resource to be created.

Creating resources requires authentication since only known users may
add resources.

## Adding Resources Without a Digital Representation

The format of the JSON used to create a resource without a digital
representation is described in the TypeScript interface
`createResourceWithoutRepresentationRequest` in module
`createResourceFormats`. It requires the IRI of the resource class the
new resource belongs to, a label describing the new resource, the IRI of
the project the new resource belongs to, and the properties to be
assigned to the new resource.

The request header's content type has to be set to `application/json`.

## Adding Resources with a Digital Representation

Certain resource classes allow for digital representations (e.g. an
image). There are two ways to attach a file to a resource: Either by
submitting directly the binaries of the file in a HTTP Multipart request
or by indicating the location of the file. The two cases are referred to
as non-GUI case and GUI case (see @ref:[Sipi and Knora](../../07-sipi/sipi-and-knora.md)).

### Including the binaries (non-GUI case)

In order to include the binaries, a HTTP Multipart request has to be
sent. One part contains the JSON (same format as described for
[Adding Resources Without a Digital Representation](#adding-resources-without-a-digital-representation)) and has to be named `json`. The other part contains the file's name, its binaries, and its mime type
and has to be named `file`. The following example illustrates how to
make this type of request using Python 3:

```python
#!/usr/bin/env python3

import requests, json

# a Python dictionary that will be turned into a JSON object
resourceParams = {
   'restype_id': 'http://www.knora.org/ontology/test#testType',
   'properties': {
       'http://www.knora.org/ontology/test#testtext': [
           {'richtext_value': {'utf8str': "test"}}
       ],
       'http://www.knora.org/ontology/test#testnumber': [
           {'int_value': 1}
       ]
   },
   'label': "test resource",
   'project_id': 'http://data.knora.org/projects/testproject'
}

# the name of the file to be submitted
filename = "myimage.jpg"

# a tuple containing the file's name, its binaries and its mimetype
file = {'file': (filename, open(filename, 'rb'), "image/jpeg")} # use name "file"

# do a POST request providing both the JSON and the binaries
r = requests.post("http://host/v1/resources",
                  data={'json': json.dumps(resourceParams)}, # use name "json"
                  files=file,
                  auth=('user', 'password'))
```

Please note that the file has to be read in binary mode (by default it
would be read in text mode).

### Indicating the location of a file (GUI case)

This request works similarly to
[Adding Resources Without a Digital Representation](#adding-resources-without-a-digital-representation). The JSON format is described
in the TypeScript interface `createResourceWithRepresentationRequest` in
module `createResourceFormats`. The request header's content type has to
set to `application/json`.

In addition to [Adding Resources Without a Digital Representation](#adding-resources-without-a-digital-representation), the
(temporary) name of the file, its original name, and mime type have to
be provided (see @ref:[GUI Case](../../07-sipi/sipi-and-knora.md#gui-case)).

## Response to a Resource Creation

When a resource has been successfully created, Knora sends back a JSON
containing the new resource's IRI (`res_id`) and its properties. The
resource IRI identifies the resource and can be used to perform future
Knora API V1 operations.

The JSON format of the response is described in the TypeScript interface
`createResourceResponse` in module `createResourceFormats`.

## Changing a resource's label

A resource's label can be changed by making a PUT request to the path
segments `resources/label`. The resource's Iri has to be provided in the
URL (as its last segment). The new label has to submitted as JSON in the
HTTP request's body.

```
HTTP PUT to http://host/v1/resources/label/resourceIRI
```

The JSON format of the request is described in the TypeScript interface
`changeResourceLabelRequest` in module `createResourceFormats`. The
response is described in the TypeScript interface
`changeResourceLabelResponse` in module `createResourceFormats`.

## Bulk Import

If you have a large amount of data to import into Knora, it can be more
convenient to use the bulk import feature than to create resources one
by one. In a bulk import operation, you submit an XML document to Knora,
describing multiple resources to be created. This is especially useful
if the resources to be created have links to one another. Knora checks
the entire request for consistency as as a whole, and performs the
update in a single database transaction.

Only system or project administrators may use the bulk import.

The procedure for using this feature is as follows:

1.  Make a request to Knora to get XML schemas describing
    the XML to be provided for the import.
2.  Convert your data into XML, including the filesystem paths of any
    files that should be attached to the resources to be created.
3.  Use an XML schema validator such as [Apache
    Xerces](http://xerces.apache.org) or
    [Saxon](http://www.saxonica.com), or an XML development environment
    such as [Oxygen](https://www.oxygenxml.com), to check that your XML
    is valid according to the schemas you got from Knora.
4.  Submit your XML to Knora.

In this procedure, the person responsible for generating the XML import
data need not be familiar with RDF or with the ontologies involved.

When Knora receives an XML import, it validates it first using the
relevant XML schemas, and then using the same internal checks that it
performs when creating any resource.

The details of the XML import format are illustrated in the following
examples.

### Bulk Import Example

Suppose we have a project with existing data (but no digital
representations), which we want to import into Knora. We have created an
ontology called `http://www.knora.org/ontology/0802/biblio` for the
project, and this ontology also uses definitions from another ontology,
called `http://www.knora.org/ontology/0801/beol`.

#### Get XML Schemas

To get XML schemas for an import, we use the following route, specifying
the IRI of our project's main ontology (in this case
`http://www.knora.org/ontology/0802/biblio`):

```
HTTP GET to http://host/v1/resources/xmlimportschemas/ontologyIRI
```

This returns a Zip archive called `p0802-biblio-xml-schemas.zip`,
containing three files:

- `p0802-biblio.xsd`: The schema for our main ontology.

- `p0801-beol.xsd`: A schema for another ontology that our main ontology depends on.

- `knoraXmlImport.xsd`: The standard Knora XML import schema, used by all XML imports.

#### Generate XML

We now convert our existing data to XML, probably by writing a custom
script. The XML looks like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0802/biblio/xml-import/v1#"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://api.knora.org/ontology/0802/biblio/xml-import/v1# p0802-biblio.xsd"
    xmlns:p0802-biblio="http://api.knora.org/ontology/0802/biblio/xml-import/v1#"
    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
    <p0801-beol:person id="abel">
        <knoraXmlImport:label>Niels Henrik Abel</knoraXmlImport:label>
        <p0801-beol:hasFamilyName knoraType="richtext_value">Abel</p0801-beol:hasFamilyName>
        <p0801-beol:hasGivenName knoraType="richtext_value">Niels Henrik</p0801-beol:hasGivenName>
        <p0801-beol:personHasTitle knoraType="richtext_value" lang="en">Sir</p0801-beol:personHasTitle>
    </p0801-beol:person>
    <p0801-beol:person id="holmes">
        <knoraXmlImport:label>Sherlock Holmes</knoraXmlImport:label>
        <p0801-beol:hasFamilyName knoraType="richtext_value">Holmes</p0801-beol:hasFamilyName>
        <p0801-beol:hasGivenName knoraType="richtext_value">Sherlock</p0801-beol:hasGivenName>
    </p0801-beol:person>
    <p0802-biblio:Journal id="math_intelligencer">
        <knoraXmlImport:label>Math Intelligencer</knoraXmlImport:label>
        <p0802-biblio:hasName knoraType="richtext_value">Math Intelligencer</p0802-biblio:hasName>
    </p0802-biblio:Journal>
    <p0802-biblio:JournalArticle id="strings_in_the_16th_and_17th_centuries">
        <knoraXmlImport:label>Strings in the 16th and 17th Centuries</knoraXmlImport:label>
        <p0802-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="http://rdfh.ch/standoff/mappings/StandardMapping">
            <text xmlns="">The most <strong>interesting</strong> article in <a class="salsah-link" href="ref:math_intelligencer">Math Intelligencer</a>.</text>
        </p0802-biblio:p0801-beol__comment>
        <p0802-biblio:endPage knoraType="richtext_value">73</p0802-biblio:endPage>
        <p0802-biblio:isPartOfJournal>
            <p0802-biblio:Journal knoraType="link_value" target="math_intelligencer" linkType="ref"/>
        </p0802-biblio:isPartOfJournal>
        <p0802-biblio:journalVolume knoraType="richtext_value">27</p0802-biblio:journalVolume>
        <p0802-biblio:publicationHasAuthor>
            <p0801-beol:person knoraType="link_value" linkType="ref" target="abel"/>
        </p0802-biblio:publicationHasAuthor>
        <p0802-biblio:publicationHasAuthor>
            <p0801-beol:person knoraType="link_value" linkType="ref" target="holmes"/>
        </p0802-biblio:publicationHasAuthor>
        <p0802-biblio:publicationHasDate knoraType="date_value">GREGORIAN:1976</p0802-biblio:publicationHasDate>
        <p0802-biblio:publicationHasTitle knoraType="richtext_value" lang="en">Strings in the 16th and 17th Centuries</p0802-biblio:publicationHasTitle>
        <p0802-biblio:publicationHasTitle knoraType="richtext_value">An alternate title</p0802-biblio:publicationHasTitle>
        <p0802-biblio:startPage knoraType="richtext_value">48</p0802-biblio:startPage>
    </p0802-biblio:JournalArticle>
</knoraXmlImport:resources>
```

This illustrates several aspects of XML imports:

  - The root XML element must be `knoraXmlImport:resources`.

  - There is an XML namespace corresponding each ontology used in the
    import. These namespaces can be found in the XML schema files
    returned by Knora.

  - We have copied and pasted
    `xmlns="http://api.knora.org/ontology/0802/biblio/xml-import/v1#"`
    from the main XML schema, `p0802-biblio.xsd`. This enables the Knora
    API server to identify the main ontology we are using.

  - We have used `xsi:schemaLocation` to indicate the main schema's
    namespace and filename. If we put our XML document in the same
    directory as the schemas, and we run an XML validator to check the
    XML, it should load the schemas.

  - The child elements of `knoraXmlImport:resources` represent resources
    to be created. The order of these elements is unimportant.

  - Each resource must have an ID, which must be an XML
    [NCName](https://www.w3.org/TR/REC-xml-names/#NT-NCName), and must
    be unique within the file. These IDs are used only during the
    import, and will not be stored in the triplestore.

  - The first child element of each resource must be a
    `knoraXmlImport:label`, which will be stored as the resource's
    `rdfs:label`.

  - Optionally, the second child element of a resource can provide
    metadata about a file to be attached to the resource (see
    bulk-import-with-digital-representations).

  - The remaining child elements of each resource represent its property
    values. These must be sorted in alphabetical order by property name.

  - If a property has mutliple values, these are represented as multiple
    adjacent property elements.

  - The type of each value must be specified using the attribute
    `knoraType`.

  - A link to another resource described in the XML import is
    represented as a child element of a property element, with
    attributes `knoraType="link_value"` and `linkType="ref"`, and a
    `target` attribute containing the ID of the target resource.

  - There is a specfic syntax for referring to properties from other
    ontologies. In the example, `p0801-beol:comment` is defined in the
    ontology `http://www.knora.org/ontology/0001/beol`. In the XML, we
    refer to it as `p0802-biblio:p0801-beol__comment`.

  - A text value can contain XML markup. If it does:

      + The text value element must have the attribute `mapping_id`,
        specifying a mapping from XML to standoff markup (see
        XML-to-standoff-mapping).
      + It is necessary to specify the appropriate XML namespace (in
        this case the null namespace, `xmlns=""`) for the XML markup
        in the text value.
      + The XML markup in the text value will not be validated by
        the schema.
      + In an XML tag that is mapped to a standoff link tag, the
        link target can refer either to the IRI of a resoruce that
        already exists in the triplestore, or to the ID of a
        resource described in the import. If a link points to a
        resource described in the import, the ID of the target
        resource must be prefixed with `ref:`. In the example above,
        using the standard mapping, the standoff link to
        `math_intelligencer` has the target
        `ref:math_intelligencer`.

  - A text value can have a `lang` attribute, whose value is an ISO 639-1
    code specifying the language of the text.

To create these resources, we use the following route, specifying the
IRI of the project in which the resources should be created:

```
HTTP POST to http://host/v1/resources/xmlimport/projectIRI
```

### Bulk Import with Links to Existing Resources

Having run the import in the previous example, we can import more data
with links to the data that is now in the triplestore:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0802/biblio/xml-import/v1#"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://api.knora.org/ontology/0802/biblio/xml-import/v1# p0802-biblio.xsd"
    xmlns:p0802-biblio="http://api.knora.org/ontology/0802/biblio/xml-import/v1#"
    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
    <p0802-biblio:JournalArticle id="strings_in_the_18th_century">
        <knoraXmlImport:label>Strings in the 18th Century</knoraXmlImport:label>
        <p0802-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="http://rdfh.ch/standoff/mappings/StandardMapping">
            <text xmlns="">The most <strong>boring</strong> article in <a class="salsah-link" href="http://rdfh.ch/biblio/QMDEHvBNQeOdw85Z2NSi9A">Math Intelligencer</a>.</text>
        </p0802-biblio:p0801-beol__comment>
        <p0802-biblio:endPage knoraType="richtext_value">76</p0802-biblio:endPage>
        <p0802-biblio:isPartOfJournal>
            <p0802-biblio:Journal knoraType="link_value" linkType="iri" target="http://rdfh.ch/biblio/QMDEHvBNQeOdw85Z2NSi9A"/>
        </p0802-biblio:isPartOfJournal>
        <p0802-biblio:journalVolume knoraType="richtext_value">27</p0802-biblio:journalVolume>
        <p0802-biblio:publicationHasAuthor>
            <p0801-beol:person knoraType="link_value" linkType="iri" target="http://rdfh.ch/biblio/c-xMB3qkRs232pWyjdUUvA"/>
        </p0802-biblio:publicationHasAuthor>
        <p0802-biblio:publicationHasDate knoraType="date_value">GREGORIAN:1977</p0802-biblio:publicationHasDate>
        <p0802-biblio:publicationHasTitle knoraType="richtext_value">Strings in the 18th Century</p0802-biblio:publicationHasTitle>
        <p0802-biblio:startPage knoraType="richtext_value">52</p0802-biblio:startPage>
    </p0802-biblio:JournalArticle>
</knoraXmlImport:resources>
```

Note that in the link elements referring to existing resources, the
`linkType` attribute has the value `iri`, and the `target` attribute
contains the IRI of the target resource.

### Bulk Import of Resources with Digital Representations

To attach a digital representation to a resource, we must provide the
element `knoraXmlImport:file` before the property elements. In this
element, we must give the absolute filesystem path to the file that
should be attached to the resource, along with its MIME type:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/incunabula/xml-import/v1#"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://api.knora.org/ontology/incunabula/xml-import/v1# incunabula.xsd"
    xmlns:incunabula="http://api.knora.org/ontology/incunabula/xml-import/v1#"
    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
    <incunabula:book id="test_book">
        <knoraXmlImport:label>a book with one page</knoraXmlImport:label>
        <incunabula:title knoraType="richtext_value">the title of a book with one page</incunabula:title>
    </incunabula:book>
    <incunabula:page id="test_page">
        <knoraXmlImport:label>a page with an image</knoraXmlImport:label>
        <knoraXmlImport:file path="/usr/local/share/import-images/incunabula/12345.tiff" mimetype="image/tiff"/>
        <incunabula:origname knoraType="richtext_value">Chlaus</incunabula:origname>
        <incunabula:pagenum knoraType="richtext_value">1a</incunabula:pagenum>
        <incunabula:partOf>
            <incunabula:book knoraType="link_value" linkType="ref" ref="test_book"/>
        </incunabula:partOf>
        <incunabula:seqnum knoraType="int_value">1</incunabula:seqnum>
    </incunabula:page>
</knoraXmlImport:resources>
```

During the processing of the bulk import, Knora will
communicate the location of file to Sipi, which will convert it to JPEG 2000
for storage.
