package com.example.game3d.terrain.io;

import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TerrainJsonCodecTest {
    private final TerrainJsonCodec codec = new TerrainJsonCodec();

    @Test public void roundTripIsDeterministicAndSortsParameterKeys() throws Exception {
        java.util.LinkedHashMap<String, Double> parameters = new java.util.LinkedHashMap<>();
        parameters.put("height", 1.2);
        parameters.put("baseOffset", 0.1);
        StructureDocument source = new StructureDocument(1, "structure.test", GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(
                        "00000000-0000-0000-0000-000000000001", true, 3, 7, 0,
                        "DEFAULT", 0.8, 1.1)),
                Collections.singletonList(new AddonReservation(
                        "00000000-0000-0000-0000-000000000002", AddonKind.DEATH_SPIKE,
                        Placement.normalized("00000000-0000-0000-0000-000000000001", .5, .25),
                        null, parameters)));

        String first = codec.encode(source);
        String second = codec.encode(codec.decode(first));

        assertEquals(first, second);
        org.junit.Assert.assertTrue(first.indexOf("baseOffset") < first.indexOf("height"));
    }

    @Test(expected = CodecException.class)
    public void rejectsUnknownFields() throws Exception {
        codec.decode("{\"documentType\":\"catalog\",\"formatVersion\":1,\"id\":\"x\","
                + "\"entries\":[],\"typo\":true}");
    }

    @Test public void readerAndWriterOverloadsMatchStringApi() throws Exception {
        StructureDocument source = new StructureDocument(1, "reader-writer", GridMode.BASIC,
                Collections.emptyList(), Collections.emptyList());
        StringWriter writer = new StringWriter();
        codec.encode(source, writer);
        StructureDocument decoded = (StructureDocument) codec.decode(new StringReader(writer.toString()));
        assertEquals(codec.encode(source), codec.encode(decoded));
    }

    @Test public void semanticErrorsAreSeparateFromDecoding() throws Exception {
        String json = "{\"documentType\":\"structure\",\"formatVersion\":1,\"id\":\"draft\","
                + "\"gridMode\":\"BASIC\",\"tiles\":[{"
                + "\"sourceId\":\"00000000-0000-0000-0000-000000000001\",\"solid\":true,"
                + "\"turnDeltaDegrees\":0,\"absoluteSlopeDegrees\":95,\"liftBefore\":0,"
                + "\"surfaceKind\":\"DEFAULT\",\"alpha\":2,\"brightness\":1}],\"addons\":[]}";
        StructureDocument decoded = codec.decodeStructure(json);
        assertFalse(new TerrainValidator().validate(decoded).isValid());
        assertEquals(codec.encode(decoded), codec.encode(codec.decode(codec.encode(decoded))));
    }

    @Test public void encoderRejectsNonFiniteNumbersWithAnExactPath() {
        StructureDocument source = new StructureDocument(1, "non-finite", GridMode.BASIC,
                Collections.singletonList(new TileRecord(
                        "00000000-0000-0000-0000-000000000001", true,
                        Double.NaN, 0, 0, "NORMAL", 1, 1)), Collections.emptyList());

        try {
            codec.encode(source);
            fail("Expected non-finite encoding to fail");
        } catch (TerrainEncodingException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains(
                    "$.tiles[0].turnDeltaDegrees"));
        }
    }

    @Test public void decoderRejectsJsonNumbersOutsideFiniteDoubleRange() {
        String json = "{\"documentType\":\"structure\",\"formatVersion\":1,"
                + "\"id\":\"draft\",\"gridMode\":\"BASIC\",\"tiles\":[{"
                + "\"sourceId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"solid\":true,\"turnDeltaDegrees\":1e999,"
                + "\"absoluteSlopeDegrees\":0,\"liftBefore\":0,"
                + "\"surfaceKind\":\"NORMAL\",\"alpha\":1,\"brightness\":1}],"
                + "\"addons\":[]}";
        try {
            codec.decodeStructure(json);
            fail("Expected out-of-range number to fail");
        } catch (CodecException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("number must be finite"));
        }
    }

    @Test public void placementFactoryAndEncoderGuardAddonNumbers() {
        TileRecord tile = new TileRecord(
                "00000000-0000-0000-0000-000000000001", true,
                0, 0, 0, "NORMAL", 1, 1);
        try {
            Placement.normalized(tile.sourceId(), Double.NaN, .5);
            fail("Expected non-finite placement creation to fail");
        } catch (IllegalArgumentException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("finite"));
        }

        java.util.Map<String, Double> parameters = new java.util.HashMap<>();
        parameters.put("height", Double.POSITIVE_INFINITY);
        AddonReservation badParameter = new AddonReservation(
                "00000000-0000-0000-0000-000000000003", AddonKind.DEATH_SPIKE,
                Placement.normalized(tile.sourceId(), .5, .5), null, parameters);
        try {
            codec.encode(new StructureDocument(1, "bad-parameter", GridMode.ADVANCED,
                    Collections.singletonList(tile), Collections.singletonList(badParameter)));
            fail("Expected non-finite parameter to fail");
        } catch (TerrainEncodingException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains(
                    "$.addons[0].parameters.height"));
        }
    }
}
