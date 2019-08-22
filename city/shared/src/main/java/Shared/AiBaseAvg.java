package Shared;

public class AiBaseAvg {
    enum BuildType
    {
        RETAIL(13),
        APARTMENT(14);
        private int value;
        BuildType(int i)
        {
            this.value = i;
        }

        public int getValue()
        {
            return value;
        }
    }
    enum GoodsMainType
    {
        MAIN_FOOD(2251),
        SUB_FOOD(2252),
        CLOTHING(2253),
        ACCESSORY(2254),
        SPORT(2255),
        DIGITAL(2256);
        private int value;
        GoodsMainType(int i)
        {
            this.value = i;
        }

        public int getValue()
        {
            return value;
        }
    }
    enum Type
    {
        BRAND(1),
        QUALITY(2);
        private int value;
        Type(int i)
        {
            this.value = i;
        }
        public int getValue()
        {
            return value;
        }
    }
}
