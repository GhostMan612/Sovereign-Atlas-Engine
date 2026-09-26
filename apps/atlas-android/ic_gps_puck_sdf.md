# ic_gps_puck_sdf — MapLibre SDF puck asset

Pre-baked 32x32 grayscale Signed Distance Field for the live GPS puck,
stored in `drawable-nodpi` so Android applies NO density scaling (scaling
would destroy the SDF encoding). Loaded with `BitmapFactory.decodeResource`
and registered via `style.addImage("gps-puck-icon", bitmap, true)`.

## MapLibre SDF Spec

alpha = 255 * (1 - 0.25 - d/8) with the vector edge occurring precisely
at alpha 191.

Sign convention used here: d is POSITIVE OUTSIDE the shape, so the
interior is opaque (255 deep inside), the edge sits at 191, and the field
fades to 0 outside. The tint comes from `iconColor` on the layer; the SDF
shader needs the smooth gradient, not a hard mask.

## Reference SDF Generator Script

```python
# Reference SDF Generator Script
import numpy as np
from scipy.ndimage import distance_transform_edt
from PIL import Image

def generate_sdf(high_res_mask_path, out_path, downscale=8, spread=8):
    mask = np.array(Image.open(high_res_mask_path).convert('L')) > 127
    dist_inside = distance_transform_edt(mask)
    dist_outside = distance_transform_edt(~mask)
    dist = dist_inside - dist_outside

    # MapLibre formula: alpha = 255 * (1 - 0.25 - d/8) => edge at 191
    alpha = 255 * (1 - 0.25 - (dist / spread))
    alpha = np.clip(alpha, 0, 255).astype(np.uint8)

    img = Image.fromarray(alpha, mode='L')
    img.resize((img.width // downscale, img.height // downscale), Image.BILINEAR).save(out_path)
```

NOTE (build environment, 2026-09): the shipped PNG was baked with an
equivalent stdlib-only analytic generator (no numpy/scipy/PIL on the
build host), computing the signed distance to the arrow polygon directly
with d positive-outside per the convention above. If the puck ever renders
inverted on-device (hollow arrow on solid field), flip the sign of d and
re-bake.
