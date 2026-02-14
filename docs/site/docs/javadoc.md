# Javadoc

The generated Javadoc API documentation is available at
[/javadoc/](/javadoc/index.html).

Javadoc is generated from source comments using the `maven-javadoc-plugin`:

```bash
./mvnw javadoc:javadoc
```

The generated output is placed alongside the MkDocs site at
`docs/site/site/javadoc/` so both are served from the same GitHub Pages
deployment.
