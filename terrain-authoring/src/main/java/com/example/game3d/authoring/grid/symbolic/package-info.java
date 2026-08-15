/**
 * Optional CPU-only extraction of the original SymbolicGrid reservation engine.
 *
 * <p>The production authoring interpreter still uses its compact occupancy matrix, whose contract
 * tests define placement semantics. These classes preserve the optimized basic/advanced segment
 * indexes for future opt-in use and performance work. Every materialization owns a
 * {@code PartialSegmentHandlerResourcePack}; there are no process-global pools or random sources.</p>
 */
package com.example.game3d.authoring.grid.symbolic;
