import abc


class Manager(abc.ABC):

    @abc.abstractmethod
    def update_state(self, state: object) -> None:
        """
    :param state:
    :return:
    """
