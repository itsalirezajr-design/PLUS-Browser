package com.plus.browser
import org.junit.Assert.*
import org.junit.Test
class MultipartTest {
    @Test fun recognizesPartNames() {
        assertEquals(MultipartPart(".zip",1),parseMultipart("Part01.zip"))
        assertEquals(MultipartPart("archive",2),parseMultipart("archive.002"))
        assertEquals(MultipartPart(".rar",3),parseMultipart("part_3.rar"))
        assertEquals(MultipartPart("file.zip",2),parseMultipart("file-02.zip"))
        assertEquals(MultipartPart("backup.rar",12),parseMultipart("backup.part12.rar"))
        assertNull(parseMultipart("ordinary.pdf"))
        assertNull(parseMultipart("part_999999999999999999999.rar"))
    }
}
