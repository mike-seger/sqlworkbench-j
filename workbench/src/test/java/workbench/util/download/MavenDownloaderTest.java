/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.util.download;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class MavenDownloaderTest
{

  @Test
  public void testMergeLists()
  {
    List<MavenArtefact> builtIn = new ArrayList<>();
    List<MavenArtefact> external = new ArrayList<>();


    MavenArtefact a1 = new MavenArtefact("g1", "a1", "v1");
    a1.setDriverClassName("class1");
    MavenArtefact a2 = new MavenArtefact("g2", "a2", "v1");
    a2.setDriverClassName("class2");
    builtIn.add(a1);
    builtIn.add(a2);

    MavenArtefact e1 = new MavenArtefact("g12", "a1", "v1");
    e1.setDriverClassName("class1");
    MavenArtefact e2 = new MavenArtefact("g22", "a2", "v1");
    e2.setDriverClassName("class3");
    external.add(e1);
    external.add(e2);

    MavenDownloader md = new MavenDownloader();
    md.mergeArtefacts(builtIn, external);

    assertEquals(3, builtIn.size());
    assertNotNull(md.searchByClassName(builtIn, "class3"));

    MavenArtefact ma = md.searchByClassName(builtIn, "class1");
    assertEquals("g12", ma.getGroupId());
    ma = md.searchByClassName(builtIn, "class2");
    assertEquals("g2", ma.getGroupId());
  }

}
