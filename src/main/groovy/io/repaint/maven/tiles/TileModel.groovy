package io.repaint.maven.tiles
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.XmlUtil
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.codehaus.plexus.util.xml.Xpp3Dom
/**
 * This will parse a tile.xml file with the intent of removing extra syntax, holding onto it and then
 * pushing the rest into a standard model. We could have used a Delegate or a Mixin here potentially, but
 * its probably clearer this way.
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class TileModel {
	Model model
	List<String> tiles = []
	File tilePom

	/**
	 * Load in the tile, grab the tiles from it if any, delete them
	 * and return a new StringReader representing the pom.
	 * @return
	 */
	@CompileStatic(TypeCheckingMode.SKIP)
	Reader strippedPom() {
		return tilePom.withReader { Reader reader ->
			def slurper = new XmlSlurper(false, false).parse(reader)

			if (slurper.tiles) {
				slurper.tiles.tile.each { tile ->
					tiles.add(tile.text())
				}

				slurper.tiles.replaceNode {}
			}

			StringWriter writer = new StringWriter()
			XmlUtil.serialize(slurper, writer)

			return new StringReader(writer.toString())
		}
	}

	public void loadTile(File tilePom) {
		this.tilePom = tilePom

		MavenXpp3Reader pomReader = new MavenXpp3Reader()

		model = pomReader.read(strippedPom())
	}

	public TileModel() {}
	public TileModel(File tilePom, Artifact artifact) {
		loadTile(tilePom)

		// this is in the artifact but isn't actually in the file, we need it
		// so we can pass it through the parent structure in the TilesModelResolverImpl

		model.version = artifact.version
		model.groupId = artifact.groupId
		model.artifactId = artifact.artifactId
		model.packaging = "pom"

		// Update each tile'd plugin's execution id with the tile GAV for easier debugging/tracing
		if (model.build) {
			if (model.build.plugins) {
				model.build.plugins.each { plugin ->
					if (plugin.executions) {
						plugin.executions.each { execution ->
							Xpp3Dom configuration = execution.configuration as Xpp3Dom
							if (configuration?.getAttribute("tiles-keep-id") == "true") {
								// do not rewrite the current execution id
								return
							}
							execution.id = GavUtil.artifactGav(artifact) + "::" + execution.id
						}
					}
				}
			}
		}

	}
}
