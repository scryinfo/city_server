package Game.Technology;

import Game.Meta.MetaItem;
import Game.Meta.MetaScienceItem;

import javax.persistence.Convert;
import javax.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/*研究所科技资料分类*/
@Embeddable
public class ScienceItemKey {
    @Convert(converter = MetaItem.Converter.class)
    public MetaItem meta;
    public ScienceItemKey() {}

    public ScienceItemKey(MetaItem mi, UUID pid) {
        this.meta = mi;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScienceItemKey itemKey = (ScienceItemKey) o;
        return Objects.equals(meta, itemKey.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meta);
    }

    public int getkeyId(){
        return this.meta.id;
    }

}
